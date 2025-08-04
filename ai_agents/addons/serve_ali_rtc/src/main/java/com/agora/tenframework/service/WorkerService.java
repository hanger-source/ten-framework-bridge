package com.agora.tenframework.service;

import com.agora.tenframework.config.Constants;
import com.agora.tenframework.config.ServerConfig;
import com.agora.tenframework.model.Prop;
import com.agora.tenframework.model.Worker;
import com.agora.tenframework.model.WorkerUpdateReq;
import com.agora.tenframework.model.request.GenerateTokenRequest;
import com.agora.tenframework.model.request.StartRequest;
import com.agora.tenframework.model.request.StopRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Worker服务类 - 基于Go代码的完整实现
 * 负责管理Worker进程的创建、启动、停止和更新
 *
 * @author Agora IO
 * @version 1.0.0
 */
@Service
public class WorkerService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerService.class);

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private TokenService tokenService;

    // 存储所有活跃的Worker实例
    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    // HTTP服务器端口计数器，用于分配唯一端口
    private final AtomicInteger httpServerPort = new AtomicInteger(10000);

    // HTTP服务器端口范围配置
    private static final int HTTP_SERVER_PORT_MAX = 30000;
    private static final int HTTP_SERVER_PORT_MIN = 10000;

    // Worker执行路径和HTTP服务器URL配置
    private static final String WORKER_EXEC = "/app/agents/bin/start";
    private static final String WORKER_HTTP_SERVER_URL = "http://127.0.0.1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取下一个可用的HTTP服务器端口
     * 当端口达到最大值时，重置到最小值
     */
    public int getNextHttpServerPort() {
        int port = httpServerPort.getAndIncrement();
        if (port > HTTP_SERVER_PORT_MAX) {
            httpServerPort.set(HTTP_SERVER_PORT_MIN);
            port = HTTP_SERVER_PORT_MIN;
        }
        return port;
    }

    /**
     * 创建新的Worker实例 - 相当于Go的newWorker函数
     *
     * @param channelName      频道名称
     * @param logFile          日志文件路径
     * @param log2Stdout       是否输出到标准输出
     * @param propertyJsonFile 属性JSON文件路径
     * @return 新创建的Worker实例
     */
    public Worker newWorker(String channelName, String logFile, boolean log2Stdout, String propertyJsonFile) {
        Worker worker = new Worker();
        worker.setChannelName(channelName);
        worker.setLogFile(logFile);
        worker.setLog2Stdout(log2Stdout);
        worker.setPropertyJsonFile(propertyJsonFile);
        worker.setCreateTs(System.currentTimeMillis() / 1000);
        worker.setUpdateTs(System.currentTimeMillis() / 1000);
        worker.setQuitTimeoutSeconds(60); // 默认值，与Go的newWorker函数保持一致
        return worker;
    }

    /**
     * 启动Worker - 相当于Go的worker.start方法
     *
     * @param request 启动请求参数
     * @return 启动的Worker实例
     * @throws Exception 启动过程中的异常
     */
    public Worker startWorker(StartRequest request) throws Exception {
        String channelName = request.getChannelName();

        logger.info("开始启动Worker - 当前运行中Worker数量: {}, 请求ID: {}", getWorkersSize(), request.getRequestId());

        // 检查channel是否为空
        if (channelName == null || channelName.isEmpty()) {
            logger.error("启动Worker失败：频道名称为空 - channelName: {}, requestId: {}", channelName,
                    request.getRequestId());
            throw new RuntimeException("channel_name是必需的参数");
        }

        // 检查是否已存在worker
        if (containsWorker(channelName)) {
            logger.error("启动Worker失败：频道已存在 - channelName: {}, requestId: {}", channelName,
                    request.getRequestId());
            throw new RuntimeException("频道已存在: " + channelName);
        }

        // 设置Worker HTTP服务器端口 - 相当于Go的getHttpServerPort()
        request.setWorkerHttpServerPort(getNextHttpServerPort());

        // 处理属性文件 - 相当于Go的processProperty方法
        String[] result = processProperty(request);
        String propertyJsonFile = result[0];
        String logFile = result[1];

        logger.info("属性文件处理完成 - propertyFile: {}, logFile: {}", propertyJsonFile, logFile);

        // 创建Worker实例
        Worker worker = newWorker(channelName, logFile, serverConfig.isLog2Stdout(), propertyJsonFile);
        worker.setHttpServerPort(request.getWorkerHttpServerPort());
        worker.setGraphName(request.getGraphName());

        // 模仿Go版本的处理逻辑：当值为null或<=0时使用默认配置
        if (request.getQuitTimeoutSeconds() != null && request.getQuitTimeoutSeconds() > 0) {
            worker.setQuitTimeoutSeconds(request.getQuitTimeoutSeconds());
        } else {
            worker.setQuitTimeoutSeconds(serverConfig.getWorkerQuitTimeoutSeconds());
        }

        logger.info("Worker创建完成 - channelName: {}, httpPort: {}, graphName: {}",
                channelName, worker.getHttpServerPort(), worker.getGraphName());

        // 启动Worker进程 - 相当于Go的worker.start(&req)
        startWorkerProcess(worker, request);

        // 只有在成功启动后才添加到map中 - 相当于Go的workers.SetIfNotExist
        // 注意：Go的SetIfNotExist只在key不存在时设置，但我们使用putIfAbsent保持一致性
        workers.putIfAbsent(channelName, worker);
        worker.setUpdateTs(System.currentTimeMillis() / 1000);

        logger.info("Worker启动成功 - channelName: {}, pid: {}, port: {}",
                channelName, worker.getPid(), worker.getHttpServerPort());

        logger.info("Worker启动完成 - 当前运行中Worker数量: {}, worker: {}, requestId: {}",
                getWorkersSize(), worker, request.getRequestId());

        return worker;
    }

    /**
     * 停止Worker - 相当于Go的worker.stop方法
     *
     * @param channelName 频道名称
     * @param requestId   请求ID
     * @throws Exception 停止过程中的异常
     */
    public void stopWorker(String channelName, String requestId) throws Exception {
        Worker worker = workers.get(channelName);

        if (worker == null) {
            throw new RuntimeException("未找到频道对应的Worker: " + channelName);
        }

        logger.info("开始停止Worker - channelName: {}, requestId: {}, pid: {}", channelName, requestId,
                worker.getPid());

        // 杀死进程 - 使用worker中存储的PID，这个PID是getProcessIdWithRetry返回的子进程PID
        if (worker.getPid() != null) {
            killProcess(worker.getPid());
        }

        // 移除Worker - 相当于Go的workers.Remove(channelName)
        workers.remove(channelName);

        logger.info("Worker停止完成 - channelName: {}, worker: {}, requestId: {}", channelName, worker, requestId);
    }

    /**
     * 更新Worker - 相当于Go的worker.update方法
     *
     * @param request 更新请求参数
     * @throws Exception 更新过程中的异常
     */
    public void updateWorker(WorkerUpdateReq request) throws Exception {
        String channelName = request.getChannelName();
        Worker worker = workers.get(channelName);

        if (worker == null) {
            throw new RuntimeException("未找到频道对应的Worker: " + channelName);
        }

        logger.info("开始更新Worker - channelName: {}, requestId: {}", channelName, request.getRequestId());

        // 发送HTTP请求到Worker - 相当于Go的worker.update方法
        String url = String.format("%s:%d/cmd", WORKER_HTTP_SERVER_URL, worker.getHttpServerPort());

        try {
            // 创建HTTP客户端并发送请求 - 相当于Go的resty客户端
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String jsonBody = objectMapper.writeValueAsString(request);

            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(httpRequest,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(String.format("HTTP状态码异常，状态码: %d", response.statusCode()));
            }

            logger.info("Worker更新完成 - channelName: {}, worker: {}, requestId: {}",
                    channelName, worker, request.getRequestId());
        } catch (Exception e) {
            logger.error("Worker更新失败 - 错误: {}, channelName: {}, requestId: {}",
                    e.getMessage(), channelName, request.getRequestId());
            throw e;
        }
    }

    /**
     * 获取所有Worker列表
     *
     * @return Worker列表
     */
    public List<Worker> getAllWorkers() {
        return new ArrayList<>(workers.values());
    }

    /**
     * 根据频道名称获取Worker
     *
     * @param channelName 频道名称
     * @return Worker实例，如果不存在返回null
     */
    public Worker getWorker(String channelName) {
        return workers.get(channelName);
    }

    /**
     * 检查Worker是否存在
     *
     * @param channelName 频道名称
     * @return 如果存在返回true，否则返回false
     */
    public boolean containsWorker(String channelName) {
        return workers.containsKey(channelName);
    }

    /**
     * 获取Worker数量
     *
     * @return 当前运行的Worker数量
     */
    public int getWorkersSize() {
        return workers.size();
    }

    /**
     * 清理所有Worker
     * 停止所有Worker并杀死冗余进程
     */
    public void cleanAllWorkers() {
        logger.info("开始清理所有Worker");

        // 停止所有Worker - 相当于Go的CleanWorkers逻辑
        for (Worker worker : workers.values()) {
            try {
                stopWorker(worker.getChannelName(), java.util.UUID.randomUUID().toString());
                logger.info("Worker清理成功 - channelName: {}, worker: {}", worker.getChannelName(),
                        worker);
            } catch (Exception e) {
                logger.error("Worker清理失败 - channelName: {}, worker: {}", worker.getChannelName(), worker,
                        e);
            }
        }

        // 获取运行中的进程，使用特定的命令模式
        Set<Integer> runningPIDs = getRunningWorkerPIDs();

        // 创建映射表便于查找
        Map<Integer, Worker> workerMap = new HashMap<>();
        for (Worker worker : workers.values()) {
            if (worker.getPid() != null) {
                workerMap.put(worker.getPid(), worker);
            }
        }

        // 杀死运行中但不在workers列表中的进程
        for (Integer pid : runningPIDs) {
            if (!workerMap.containsKey(pid)) {
                logger.info("杀死冗余进程 - pid: {}", pid);
                killProcess(pid);
            }
        }

        workers.clear();
    }

    /**
     * 处理属性文件 - 相当于Go的processProperty方法
     *
     * @param request 启动请求参数
     * @return 返回属性JSON文件路径和日志文件路径的数组
     * @throws Exception 处理过程中的异常
     */
    private String[] processProperty(StartRequest request) throws Exception {
        // 读取property.json文件
        String content = new String(Files.readAllBytes(Paths.get(Constants.PROPERTY_JSON_FILE)));
        Map<String, Object> propertyJson = objectMapper.readValue(content,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });

        // 获取图名称
        String graphName = request.getGraphName();
        if (graphName == null || graphName.isEmpty()) {
            throw new RuntimeException("graph_name是必需的参数");
        }

        // 处理token逻辑 - 相当于Go的token处理逻辑
        // 如果请求中提供了token，直接使用；否则生成token
        if (request.getToken() != null && !request.getToken().isEmpty()) {
            // 使用提供的token
            logger.info("使用提供的token - requestId: {}", request.getRequestId());
        } else {
            // 只有在没有提供token时才生成token
            String appId = serverConfig.getAppId();
            String appCertificate = serverConfig.getAppCertificate();

            request.setToken(appId);
            if (appCertificate != null && !appCertificate.isEmpty()) {
                try {
                    // 使用与Go的BuildTokenWithRtm相同的逻辑
                    GenerateTokenRequest tokenRequest = new GenerateTokenRequest();
                    tokenRequest.setChannelName(request.getChannelName());
                    tokenRequest.setUid(0L); // 使用0作为Worker的默认UID
                    String generatedToken = tokenService.generateToken(tokenRequest);
                    request.setToken(generatedToken);
                } catch (Exception e) {
                    logger.error("启动Worker时生成令牌失败 - requestId: {}", request.getRequestId(), e);
                    throw new RuntimeException("生成令牌失败", e);
                }
            }
        }

        // 定位预定义图数组
        Map<String, Object> tenSection = (Map<String, Object>) propertyJson.get("ten");
        if (tenSection == null) {
            throw new RuntimeException("格式无效：缺少_ten部分");
        }

        List<Object> predefinedGraphs = (List<Object>) tenSection.get("predefined_graphs");
        if (predefinedGraphs == null) {
            throw new RuntimeException("格式无效：缺少predefined_graphs或不是数组");
        }

        // 过滤匹配名称的图
        List<Object> newGraphs = new ArrayList<>();
        for (Object graph : predefinedGraphs) {
            Map<String, Object> graphMap = (Map<String, Object>) graph;
            if (graphName.equals(graphMap.get("name"))) {
                newGraphs.add(graph);
            }
        }

        if (newGraphs.isEmpty()) {
            throw new RuntimeException("未找到图: " + graphName);
        }

        // 用过滤后的数组替换predefined_graphs数组
        tenSection.put("predefined_graphs", newGraphs);

        // 自动启动
        for (Object graph : newGraphs) {
            Map<String, Object> graphMap = (Map<String, Object>) graph;
            graphMap.put("auto_start", true);
        }

        // 设置附加属性到property.json
        if (request.getProperties() != null) {
            for (Map.Entry<String, Map<String, Object>> entry : request.getProperties().entrySet()) {
                String extensionName = entry.getKey();
                Map<String, Object> props = entry.getValue();

                if (extensionName != null && !extensionName.isEmpty()) {
                    for (Map.Entry<String, Object> propEntry : props.entrySet()) {
                        String prop = propEntry.getKey();
                        Object val = propEntry.getValue();

                        // 将每个属性设置到相应的图和属性中
                        for (Object graph : newGraphs) {
                            Map<String, Object> graphMap = (Map<String, Object>) graph;
                            Map<String, Object> graphData = (Map<String, Object>) graphMap.get("graph");
                            List<Object> nodes = (List<Object>) graphData.get("nodes");

                            for (Object node : nodes) {
                                Map<String, Object> nodeMap = (Map<String, Object>) node;
                                if (extensionName.equals(nodeMap.get("name"))) {
                                    Map<String, Object> properties = (Map<String, Object>) nodeMap.get("property");
                                    properties.put(prop, val);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 设置启动参数到property.json
        setStartParameters(newGraphs, request);

        // 验证"nodes"部分中的环境变量
        validateEnvironmentVariables(newGraphs, request);

        // 将修改后的JSON序列化回字符串 - 使用格式化的JSON，类似Go的json.MarshalIndent
        String modifiedPropertyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(propertyJson);

        // 生成时间戳 - 使用与Go相同的格式："20060102_150405_000"
        String ts = String.format("%tY%<tm%<td_%<tH%<tM%<tS_000", new Date());
        String propertyJsonFile = String.format("%s/property-%s-%s.json",
                serverConfig.getLogPath(), java.net.URLEncoder.encode(request.getChannelName(), "UTF-8"), ts);
        String logFile = String.format("%s/app-%s-%s.log",
                serverConfig.getLogPath(), java.net.URLEncoder.encode(request.getChannelName(), "UTF-8"), ts);

        // 写入文件
        Files.write(Paths.get(propertyJsonFile), modifiedPropertyJson.getBytes());

        return new String[] { propertyJsonFile, logFile };
    }

    /**
     * 设置启动参数 - 相当于Go的startPropMap逻辑
     *
     * @param graphs  图列表
     * @param request 启动请求参数
     */
    private void setStartParameters(List<Object> graphs, StartRequest request) {
        // 使用与Go的startPropMap相同的逻辑
        for (Map.Entry<String, List<Prop>> entry : Constants.START_PROP_MAP.entrySet()) {
            String fieldName = entry.getKey();
            List<Prop> props = entry.getValue();

            Object val = getFieldValue(request, fieldName);
            if (val != null && !val.toString().isEmpty()) {
                for (Prop prop : props) {
                    setPropertyInGraphs(graphs, prop.getExtensionName(), prop.getProperty(), val);
                }
            }
        }
    }

    /**
     * 在图列表中设置属性
     *
     * @param graphs        图列表
     * @param extensionName 扩展名称
     * @param property      属性名
     * @param value         属性值
     */
    private void setPropertyInGraphs(List<Object> graphs, String extensionName, String property, Object value) {
        for (Object graph : graphs) {
            Map<String, Object> graphMap = (Map<String, Object>) graph;
            Map<String, Object> graphData = (Map<String, Object>) graphMap.get("graph");
            List<Object> nodes = (List<Object>) graphData.get("nodes");

            for (Object node : nodes) {
                Map<String, Object> nodeMap = (Map<String, Object>) node;
                if (extensionName.equals(nodeMap.get("name"))) {
                    Map<String, Object> properties = (Map<String, Object>) nodeMap.get("property");
                    properties.put(property, value);
                }
            }
        }
    }

    /**
     * 验证环境变量 - 相当于Go的环境变量验证
     *
     * @param graphs  图列表
     * @param request 启动请求，包含EnvProperties
     */
    private void validateEnvironmentVariables(List<Object> graphs, StartRequest request) {
        Pattern envPattern = Pattern.compile("\\$\\{env:([^}|]+)\\}");

        for (Object graph : graphs) {
            Map<String, Object> graphMap = (Map<String, Object>) graph;
            Map<String, Object> graphData = (Map<String, Object>) graphMap.get("graph");
            List<Object> nodes = (List<Object>) graphData.get("nodes");

            if (nodes == null) {
                logger.info("图中没有nodes部分");
                continue;
            }

            for (Object node : nodes) {
                Map<String, Object> nodeMap = (Map<String, Object>) node;
                Map<String, Object> properties = (Map<String, Object>) nodeMap.get("property");

                if (properties == null) {
                    continue;
                }

                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String key = entry.getKey();
                    Object val = entry.getValue();

                    if (!(val instanceof String)) {
                        continue;
                    }

                    String strVal = (String) val;
                    java.util.regex.Matcher matcher = envPattern.matcher(strVal);

                    while (matcher.find()) {
                        if (matcher.groupCount() >= 1) {
                            String variable = matcher.group(1);
                            String envValue = null;

                            // 优先从 EnvProperties 中查找
                            if (request.getEnvProperties() != null) {
                                Object value = request.getEnvProperties().get(variable);
                                if (value != null) {
                                    envValue = String.valueOf(value);
                                    logger.info("在EnvProperties中找到环境变量 - 变量: {}, 值: {}", variable, envValue);
                                    // 替换当前属性值中的占位符
                                    String newValue = strVal.replace(matcher.group(0), envValue);
                                    properties.put(key, newValue);
                                    logger.info("替换属性中的环境变量 - 变量: {}, 属性: {}, 原值: {}, 新值: {}",
                                            variable, key, strVal, newValue);
                                }
                            }

                            // 如果 EnvProperties 中没有找到，再从系统环境变量获取
                            if (envValue == null) {
                                envValue = System.getenv(variable);
                                if (envValue != null && !envValue.isEmpty()) {
                                    logger.info("在系统环境变量中找到环境变量 - 变量: {}, 值: {}", variable, envValue);
                                } else {
                                    logger.error("在EnvProperties和系统环境变量中都未找到环境变量 - 变量: {}, 属性: {}", variable, key);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 启动Worker进程 - 相当于Go的worker.start方法
     *
     * @param worker  Worker实例
     * @param request 启动请求参数
     * @throws Exception 启动过程中的异常
     */
    private void startWorkerProcess(Worker worker, StartRequest request) throws Exception {
        String shell = String.format("cd /app/agents && %s --property %s", WORKER_EXEC, worker.getPropertyJsonFile());

        logger.info("开始启动Worker进程 - requestId: {}, shell命令: {}", request.getRequestId(), shell);

        // 使用setsid创建新会话和进程组，相当于Go的Setpgid: true
        ProcessBuilder pb = new ProcessBuilder("setsid", "sh", "-c", shell);

        // 设置正确的环境变量，确保子进程能正确处理中文字符
        Map<String, String> env = pb.environment();
        env.put("LANG", "zh_CN.UTF-8");
        env.put("LC_ALL", "zh_CN.UTF-8");
        env.put("LC_CTYPE", "zh_CN.UTF-8");
        env.put("LC_MESSAGES", "zh_CN.UTF-8");
        env.put("LC_MONETARY", "zh_CN.UTF-8");
        env.put("LC_NUMERIC", "zh_CN.UTF-8");
        env.put("LC_TIME", "zh_CN.UTF-8");
        env.put("LC_COLLATE", "zh_CN.UTF-8");
        logger.info("设置环境变量 - LANG: {}, LC_ALL: {}, LC_CTYPE: {}",
                env.get("LANG"), env.get("LC_ALL"), env.get("LC_CTYPE"));

        // 设置输出处理
        File logFile = null;
        if (!worker.isLog2Stdout()) {
            logFile = new File(worker.getLogFile());
            logFile.getParentFile().mkdirs();
            logger.info("创建日志文件: {}", logFile.getAbsolutePath());
        } else {
            logger.info("Log2Stdout为true，将输出到控制台");
        }

        // 启动进程
        logger.info("即将启动进程，shell命令: {}", shell);
        Process process = pb.start();
        logger.info("进程启动成功，PID: {}", process.pid());
        logger.info("进程是否存活: {}", process.isAlive());

        // 获取进程ID
        int pid = getProcessIdWithRetry(process, request.getRequestId());
        worker.setPid(pid);

        logger.info("Worker启动完成 - pid: {}, channel: {}", pid, worker.getChannelName());

        // 使用原来的日志输出处理方式
        logger.info("开始为进程启动输出读取线程: {}", process.pid());
        startOutputReaderWithPrefix(process, "-", logFile);

        // 更新前缀为实际的channel名称
        logger.info("更新前缀为频道名称: {}", worker.getChannelName());
        updateOutputReaderPrefix(process, worker.getChannelName());

        // 监控进程
        logger.info("开始监控进程，PID: {}", process.pid());
        monitorProcess(process, worker, request.getRequestId(), logFile);
    }

    // PrefixWriter等价类 - 存储前缀和写入器
    private static class PrefixWriter {
        private volatile String prefix;
        private final OutputStreamWriter writer;

        public PrefixWriter(String prefix, OutputStreamWriter writer) {
            this.prefix = prefix;
            this.writer = writer;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public void writeLine(String line) throws IOException {
            String prefixedLine = String.format("[%s] %s%n", prefix, line);
            writer.write(prefixedLine);
            writer.flush();
        }
    }

    // 为每个进程存储stdout和stderr的PrefixWriter
    private final Map<Process, Map<String, PrefixWriter>> prefixWriters = new ConcurrentHashMap<>();

    /**
     * 启动带前缀的输出读取器 - 相当于Go的PrefixWriter，用于stdout和stderr
     *
     * @param process       进程实例
     * @param initialPrefix 初始前缀
     * @param logFile       日志文件
     */
    private void startOutputReaderWithPrefix(Process process, String initialPrefix, File logFile) {
        // 根据Log2Stdout设置设置输出流
        OutputStream outputStream = System.out; // 默认为stdout
        if (logFile != null) {
            try {
                outputStream = new FileOutputStream(logFile, true); // 追加模式
            } catch (IOException e) {
                logger.error("打开日志文件失败 - logFile: {}", logFile, e);
                outputStream = System.out; // 回退到stdout
            }
        }

        // 为stdout和stderr创建PrefixWriter实例 - 相当于Go的PrefixWriter
        PrefixWriter stdoutPrefixWriter = new PrefixWriter(initialPrefix,
                new OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8));
        PrefixWriter stderrPrefixWriter = new PrefixWriter(initialPrefix,
                new OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8));

        // 为这个进程存储两个PrefixWriter
        Map<String, PrefixWriter> writers = new HashMap<>();
        writers.put("stdout", stdoutPrefixWriter);
        writers.put("stderr", stderrPrefixWriter);
        prefixWriters.put(process, writers);

        // 启动stdout读取线程
        Thread stdoutReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                logger.info("开始stdout读取线程，进程: {}", process.pid());
                while ((line = reader.readLine()) != null) {
                    // 为每行添加前缀 - 相当于Go的PrefixWriter
                    stdoutPrefixWriter.writeLine(line);

                    // 同时记录到我们的logger用于调试
                    logger.debug("Worker stdout - 前缀: {}, 内容: {}", stdoutPrefixWriter.prefix, line);
                }
                logger.info("stdout读取线程结束，进程: {}", process.pid());
            } catch (IOException e) {
                logger.error("读取stdout时出错，进程: {}", process.pid(), e);
            }
        });
        stdoutReaderThread.setDaemon(true);
        stdoutReaderThread.start();

        // 启动stderr读取线程
        Thread stderrReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                logger.info("开始stderr读取线程，进程: {}", process.pid());
                while ((line = reader.readLine()) != null) {
                    // 添加调试日志用于字符编码问题
                    logger.debug("原始stderr行: {}", line);
                    if (line.contains("????")) {
                        logger.warn("检测到stderr中的编码问题: {}", line);
                    }

                    // 为每行添加前缀 - 相当于Go的PrefixWriter
                    stderrPrefixWriter.writeLine(line);

                    // 同时记录到我们的logger用于调试
                    logger.debug("Worker stderr - 前缀: {}, 内容: {}", stderrPrefixWriter.prefix, line);
                }
                logger.info("stderr读取线程结束，进程: {}", process.pid());
            } catch (IOException e) {
                logger.error("读取stderr时出错，进程: {}", process.pid(), e);
            } finally {
                prefixWriters.remove(process);
            }
        });
        stderrReaderThread.setDaemon(true);
        stderrReaderThread.start();
    }

    /**
     * 使用实际的频道名称更新前缀 - 相当于Go的stdout和stderr的前缀更新
     *
     * @param process     进程实例
     * @param channelName 频道名称
     */
    private void updateOutputReaderPrefix(Process process, String channelName) {
        Map<String, PrefixWriter> writers = prefixWriters.get(process);
        if (writers != null) {
            // 更新stdout和stderr的PrefixWriter - 相当于Go的前缀更新
            writers.get("stdout").setPrefix(channelName);
            writers.get("stderr").setPrefix(channelName);
            logger.debug("更新输出读取器前缀 - channelName: {}", channelName);
        }
    }

    /**
     * 获取进程ID，带重试逻辑 - 相当于Go的pgrep逻辑
     *
     * @param process   进程实例
     * @param requestId 请求ID
     * @return 进程ID
     */
    private int getProcessIdWithRetry(Process process, String requestId) {
        int pid = Math.toIntExact(process.pid());

        // 确保进程完全启动 - 相当于Go的pgrep逻辑
        String shell = String.format("pgrep -P %d", pid);
        logger.info("Worker获取PID - requestId: {}, shell命令: {}", requestId, shell);

        // 与Go版本保持一致：存储父进程PID，在stop时用负号杀死整个进程组
        // Go版本：w.Pid = pid (父进程PID)
        // Go版本：syscall.Kill(-w.Pid, syscall.SIGKILL) (杀死整个进程组)

        // 验证子进程存在
        boolean subprocessFound = false;
        for (int i = 0; i < 10; i++) { // 重试10次，与Go版本一致
            try {
                Process pgrepProcess = Runtime.getRuntime().exec(new String[] { "sh", "-c", shell });
                String output = new String(pgrepProcess.getInputStream().readAllBytes()).trim();
                int pgrepExitCode = pgrepProcess.waitFor();

                logger.debug("pgrep尝试 {} - 退出码: {}, 输出: '{}'", i + 1, pgrepExitCode, output);

                if (!output.isEmpty()) {
                    int subprocessPid = Integer.parseInt(output);
                    if (subprocessPid > 0 && isInProcessGroup(subprocessPid, pid)) {
                        logger.info("成功找到子进程PID: {}，父进程PID: {}", subprocessPid, pid);
                        subprocessFound = true;
                        break; // 如果成功找到子进程，退出循环
                    }
                }
            } catch (Exception e) {
                logger.debug("pgrep尝试 {} 失败: {}", i + 1, e.getMessage());
                // 忽略错误并继续重试
            }

            if (i < 9) { // 不是最后一次尝试
                logger.warn("Worker获取PID失败，正在重试... - 尝试次数: {}, pid: {}, requestId: {}",
                        i + 1, pid, requestId);
                try {
                    Thread.sleep(1000); // 等待1000ms，与Go版本一致
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return pid;
                }
            }
        }

        // 返回父进程PID，与Go版本保持一致
        if (!subprocessFound) {
            logger.warn("未找到子进程，但返回父进程PID: {}", pid);
        }

        return pid; // 返回父进程PID
    }

    /**
     * 在后台监控进程 - 相当于Go的goroutine监控
     *
     * @param process   进程实例
     * @param worker    Worker实例
     * @param requestId 请求ID
     * @param logFile   日志文件
     */
    private void monitorProcess(Process process, Worker worker, String requestId, File logFile) {
        Thread monitorThread = new Thread(() -> {
            try {
                logger.info("开始监控进程，PID: {}", process.pid());
                int exitCode = process.waitFor();
                logger.info("进程退出，退出码: {}，PID: {}", exitCode, process.pid());

                if (exitCode != 0) {
                    logger.error("Worker进程失败 - requestId: {}, exitCode: {}", requestId, exitCode);
                } else {
                    logger.info("Worker进程成功完成 - requestId: {}", requestId);
                }
            } catch (InterruptedException e) {
                logger.error("Worker进程监控被中断 - requestId: {}", requestId, e);
            } finally {
                // 清理资源
                if (logFile != null) {
                    try {
                        // 日志文件会在进程结束时自动关闭
                    } catch (Exception e) {
                        logger.warn("关闭日志文件时出错 - channelName: {}", worker.getChannelName(), e);
                    }
                }

                // 从map中移除worker
                workers.remove(worker.getChannelName());
                logger.info("Worker已从map中移除 - channelName: {}", worker.getChannelName());
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * 杀死进程 - 相当于Go的syscall.Kill
     * Go使用syscall.Kill(-w.Pid, syscall.SIGKILL)杀死整个进程组
     *
     * @param pid 进程ID
     */
    private void killProcess(int pid) {
        try {
            // 杀死整个进程组（相当于Go的syscall.Kill(-w.Pid, syscall.SIGKILL)）
            // 由于我们使用setsid，进程组ID与进程ID相同
            // 使用负号杀死整个进程组，与Go版本保持一致
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", "kill -KILL -" + pid });
            process.waitFor();
            logger.info("向进程组发送KILL信号 - pid: {}", pid);
        } catch (Exception e) {
            logger.error("杀死进程组时出错 - pid: {}", pid, e);
        }
    }

    /**
     * 生成日志文件路径 - 相当于Go的日志文件生成
     *
     * @param channelName 频道名称
     * @return 日志文件路径
     */
    private String generateLogFilePath(String channelName) {
        // 使用与Go相同的时间戳格式："20060102_150405_000"
        String ts = String.format("%tY%<tm%<td_%<tH%<tM%<tS_000", new Date());
        return String.format("%s/app-%s-%s.log",
                serverConfig.getLogPath(),
                java.net.URLEncoder.encode(channelName, java.nio.charset.StandardCharsets.UTF_8), ts);
    }

    /**
     * URL编码 - 相当于Go的url.QueryEscape
     *
     * @param str 要编码的字符串
     * @return 编码后的字符串
     */
    private String urlEncode(String str) {
        try {
            return java.net.URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            return str;
        }
    }

    /**
     * 从请求对象获取字段值 - 相当于Go的getFieldValue
     *
     * @param request   启动请求参数
     * @param fieldName 字段名
     * @return 字段值
     */
    private Object getFieldValue(StartRequest request, String fieldName) {
        try {
            java.lang.reflect.Field field = StartRequest.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(request);
        } catch (Exception e) {
            logger.warn("获取字段值失败: {}", fieldName, e);
            return null;
        }
    }

    /**
     * 获取运行中的Worker PID列表 - 相当于Go的getRunningWorkerPIDs函数
     *
     * @return 运行中的PID集合
     */
    private Set<Integer> getRunningWorkerPIDs() {
        Set<Integer> runningPIDs = new HashSet<>();
        try {
            // 定义查找进程的命令
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c",
                    "ps aux | grep \"bin/worker --property\" | grep -v grep" });

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length > 1) {
                        try {
                            int pid = Integer.parseInt(fields[1]); // PID通常是第二个字段
                            runningPIDs.add(pid);
                        } catch (NumberFormatException e) {
                            // 跳过无效的PID
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("获取运行中Worker PID列表失败", e);
        }
        return runningPIDs;
    }

    /**
     * 检查进程是否在进程组中 - 相当于Go的isInProcessGroup函数
     *
     * @param pid  进程ID
     * @param pgid 进程组ID
     * @return 如果进程在进程组中返回true，否则返回false
     */
    private boolean isInProcessGroup(int pid, int pgid) {
        try {
            // 获取给定PID的进程组ID
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", "ps -o pgid= -p " + pid });
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    int processPgid = Integer.parseInt(line.trim());
                    return processPgid == pgid;
                }
            }
        } catch (Exception e) {
            logger.warn("检查进程组失败 - pid: {}, pgid: {}", pid, pgid, e);
        }
        return false;
    }
}