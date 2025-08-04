package com.agora.tenframework.controller;

import com.agora.tenframework.config.Constants;
import com.agora.tenframework.config.ServerConfig;
import com.agora.tenframework.model.Code;
import com.agora.tenframework.model.Worker;
import com.agora.tenframework.model.WorkerUpdateReq;
import com.agora.tenframework.model.request.*;
import com.agora.tenframework.model.response.ApiResponse;
import com.agora.tenframework.service.TokenService;
import com.agora.tenframework.service.WorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/**
 * TEN Framework API控制器类
 *
 * 该类提供了TEN Framework的完整REST API接口，包括：
 *
 * 核心API接口：
 * 1. 健康检查 - /health 和 / 端点
 * 2. Worker管理 - /start, /stop, /list 端点
 * 3. 图形配置 - /graphs 端点
 * 4. Token生成 - /token/generate 端点
 * 5. 向量文档 - /vector/document/* 端点
 * 6. 扩展属性 - /dev-tmp/addons/default-properties 端点
 *
 * API设计原则：
 * - RESTful风格：使用标准HTTP方法和状态码
 * - 统一响应格式：所有接口返回ApiResponse包装对象
 * - 错误处理：统一的异常处理和错误码定义
 * - 日志记录：详细的请求和响应日志
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的http_server.go中的HTTP处理器
 * - 保持相同的API路径和请求/响应格式
 * - 使用Spring Boot的@RestController注解
 *
 * 安全考虑：
 * - 输入验证：所有请求参数都进行有效性验证
 * - 文件上传：限制文件大小和类型
 * - 错误信息：避免暴露敏感信息
 *
 * @author Agora IO
 * @version 1.0.0
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    // ==================== 依赖注入 ====================
    @Autowired
    private WorkerService workerService; // Worker服务

    @Autowired
    private TokenService tokenService; // Token服务

    @Autowired
    private ServerConfig serverConfig; // 服务器配置

    // ==================== JSON处理 ====================
    /**
     * JSON对象映射器
     * 用于处理请求和响应的JSON序列化/反序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 健康检查接口 ====================
    /**
     * 健康检查接口
     *
     * 用于监控TEN Framework服务器的运行状态
     * 对应Go版本的handlerHealth方法
     *
     * @return 成功响应
     */
    @GetMapping("/")
    public ResponseEntity<ApiResponse<Void>> health() {
        logger.debug("健康检查请求 - 根路径");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 健康检查接口（显式路径）
     *
     * 用于监控TEN Framework服务器的运行状态
     * 对应Go版本的handlerHealth方法
     *
     * @return 成功响应
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> healthCheck() {
        logger.debug("健康检查请求 - /health路径");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== Worker管理接口 ====================
    /**
     * 获取Worker列表接口
     *
     * 返回当前运行的所有Worker进程信息
     * 对应Go版本的handlerList方法
     *
     * @return Worker列表响应
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listWorkers() {
        logger.info("获取Worker列表请求");

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Worker worker : workerService.getAllWorkers()) {
            Map<String, Object> workerJson = new HashMap<>();
            workerJson.put("channelName", worker.getChannelName());
            workerJson.put("createTs", worker.getCreateTs());
            filtered.add(workerJson);
        }

        logger.info("Worker列表获取完成 - 数量: {}", filtered.size());
        return ResponseEntity.ok(ApiResponse.success(filtered));
    }

    // ==================== 图形配置接口 ====================
    /**
     * 获取图形配置接口
     *
     * 读取property.json文件中的图形配置信息
     * 对应Go版本的handleGraphs方法
     *
     * @return 图形配置列表响应
     */
    @GetMapping("/graphs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGraphs() {
        try {
            // 读取property.json文件并获取图形列表
            String content = new String(Files.readAllBytes(Paths.get(Constants.PROPERTY_JSON_FILE)));
            Map<String, Object> propertyJson = objectMapper.readValue(content,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> tenSection = (Map<String, Object>) propertyJson.get("ten");
            if (tenSection == null) {
                logger.error("无效的配置文件格式：缺少_ten节点");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10100", "无效的配置文件格式：缺少_ten节点"));
            }

            List<Object> predefinedGraphs = (List<Object>) tenSection.get("predefined_graphs");
            if (predefinedGraphs == null) {
                logger.error("无效的配置文件格式：缺少predefined_graphs节点");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10100", "无效的配置文件格式：缺少predefined_graphs节点"));
            }

            // 过滤图形配置
            List<Map<String, Object>> graphs = new ArrayList<>();
            for (Object graph : predefinedGraphs) {
                Map<String, Object> graphMap = (Map<String, Object>) graph;
                Map<String, Object> filteredGraph = new HashMap<>();
                filteredGraph.put("name", graphMap.get("name"));
                filteredGraph.put("uuid", graphMap.get("name"));
                filteredGraph.put("auto_start", graphMap.get("auto_start"));
                graphs.add(filteredGraph);
            }

            logger.info("图形配置获取完成 - 数量: {}", graphs.size());
            return ResponseEntity.ok(ApiResponse.success(graphs));
        } catch (Exception e) {
            logger.error("获取图形配置失败 - error: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("10106", "读取property.json文件失败"));
        }
    }

    // ==================== 扩展属性接口 ====================
    /**
     * 获取扩展默认属性接口
     *
     * 返回支持的扩展模块及其默认配置属性
     * 用于前端界面动态生成配置表单
     *
     * @return 扩展默认属性列表
     */
    @GetMapping("/dev-tmp/addons/default-properties")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAddonDefaultProperties() {
        try {
            // 获取基础目录路径
            String baseDir = "./agents/ten_packages/extension";

            // 读取基础目录下的所有文件夹
            File baseDirFile = new File(baseDir);
            if (!baseDirFile.exists() || !baseDirFile.isDirectory()) {
                logger.error("读取扩展目录失败 - 目录不存在");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10105", "读取扩展目录失败"));
            }

            File[] entries = baseDirFile.listFiles();
            if (entries == null) {
                logger.error("读取扩展目录失败 - 无法列出文件");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10105", "读取扩展目录失败"));
            }

            // 遍历每个文件夹并读取property.json文件
            List<Map<String, Object>> addons = new ArrayList<>();
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    String addonName = entry.getName();
                    String propertyFilePath = String.format("%s/%s/property.json", baseDir, addonName);
                    try {
                        String content = new String(Files.readAllBytes(Paths.get(propertyFilePath)));
                        Map<String, Object> properties = objectMapper.readValue(content, Map.class);

                        Map<String, Object> addon = new HashMap<>();
                        addon.put("addon", addonName);
                        addon.put("property", properties);
                        addons.add(addon);
                    } catch (Exception e) {
                        logger.warn("读取属性文件失败 - addon: {}, error: {}", addonName, e.getMessage());
                        continue;
                    }
                }
            }

            logger.info("扩展默认属性获取完成 - 数量: {}", addons.size());
            return ResponseEntity.ok(ApiResponse.success(addons));
        } catch (Exception e) {
            logger.error("读取扩展目录失败 - error: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("10105", "读取扩展目录失败"));
        }
    }

    // ==================== 基础操作接口 ====================
    /**
     * Ping接口
     *
     * 用于测试与TEN Framework服务器的连接状态
     * 对应Go版本的handlerPing方法
     *
     * @param request Ping请求对象
     * @return 成功响应
     */
    @PostMapping("/ping")
    public ResponseEntity<ApiResponse<Void>> ping(@RequestBody PingRequest request) {
        logger.info("Ping请求开始 - channelName: {}, requestId: {}", request.getChannelName(),
                request.getRequestId());

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("Ping请求失败 - 频道名为空, channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10004", "频道名为空", request.getRequestId()));
        }

        if (!workerService.containsWorker(request.getChannelName())) {
            logger.error("Ping请求失败 - 频道不存在, channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10002", "频道不存在", request.getRequestId()));
        }

        // 更新Worker
        Worker worker = workerService.getWorker(request.getChannelName());
        worker.setUpdateTs(System.currentTimeMillis() / 1000);

        logger.info("Ping请求完成 - worker: {}, requestId: {}", worker, request.getRequestId());
        return ResponseEntity.ok(ApiResponse.success(null, request.getRequestId()));
    }

    /**
     * 启动Worker接口
     *
     * 启动指定频道的AI代理Worker进程
     * 对应Go版本的handlerStart方法
     *
     * @param request 启动请求对象
     * @return 启动结果响应
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Void>> startWorker(@RequestBody StartRequest request) {
        int workersRunning = workerService.getWorkersSize();

        logger.info("启动Worker请求开始 - 当前运行Worker数: {}", workersRunning);

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("启动Worker失败 - 频道名为空, channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10004", "频道名为空", request.getRequestId()));
        }

        if (workersRunning >= serverConfig.getWorkersMax()) {
            logger.error("启动Worker失败 - Worker数量超限, 当前: {}, 最大: {}, requestId: {}",
                    workersRunning, serverConfig.getWorkersMax(), request.getRequestId());
            return ResponseEntity.status(429).body(ApiResponse.error("10001", "Worker数量超限", request.getRequestId()));
        }

        if (workerService.containsWorker(request.getChannelName())) {
            logger.error("启动Worker失败 - 频道已存在, channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10003", "频道已存在", request.getRequestId()));
        }

        // 检查graphName是否包含"gemini"
        if (request.getGraphName() != null && request.getGraphName().contains("gemini")) {
            // 统计使用相同graphName的Worker数量
            int graphNameCount = 0;
            for (Worker worker : workerService.getAllWorkers()) {
                if (request.getGraphName().equals(worker.getGraphName())) {
                    graphNameCount++;
                }
            }

            // 如果超过3个Worker使用相同的graphName则拒绝
            if (graphNameCount >= Constants.MAX_GEMINI_WORKER_COUNT) {
                logger.error("启动Worker失败 - graphName Worker数量超限, graphName: {}, 数量: {}",
                        request.getGraphName(), graphNameCount);
                return ResponseEntity.status(429)
                        .body(ApiResponse.error("10001", "Worker数量超限", request.getRequestId()));
            }
        }

        try {
            Worker worker = workerService.startWorker(request);
            logger.info("启动Worker请求完成 - 当前运行Worker数: {}, worker: {}, requestId: {}",
                    workerService.getWorkersSize(), worker, request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(null, request.getRequestId()));
        } catch (Exception e) {
            logger.error("启动Worker失败 - requestId: {}, error: {}", request.getRequestId(), e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("10101", "启动Worker失败", request.getRequestId()));
        }
    }

    /**
     * 停止Worker接口
     *
     * 停止指定频道的AI代理Worker进程
     * 对应Go版本的handlerStop方法
     *
     * @param request 停止请求对象
     * @return 停止结果响应
     */
    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<Void>> stopWorker(@RequestBody StopRequest request) {
        logger.info("停止Worker请求开始 - 请求: {}", request);

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("停止Worker失败 - 频道名为空, channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10004", "频道名为空", request.getRequestId()));
        }

        if (!workerService.containsWorker(request.getChannelName())) {
            logger.error("停止Worker失败 - 频道不存在, channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10002", "频道不存在", request.getRequestId()));
        }

        try {
            workerService.stopWorker(request.getChannelName(), request.getRequestId());
            logger.info("停止Worker请求完成 - requestId: {}", request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(null, request.getRequestId()));
        } catch (Exception e) {
            logger.error("停止Worker失败 - worker: {}, requestId: {}, error: {}",
                    workerService.getWorker(request.getChannelName()), request.getRequestId(), e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("10102", "停止Worker失败", request.getRequestId()));
        }
    }

    // ==================== Token生成接口 ====================
    /**
     * 生成Token接口
     *
     * 为Agora RTC/RTM服务生成访问Token
     * 对应Go版本的handlerGenerateToken方法
     *
     * @param request Token生成请求对象
     * @return Token生成结果
     */
    @PostMapping("/token/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateToken(@RequestBody GenerateTokenRequest request) {
        logger.info("生成Token请求开始 - 请求: {}", request);

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("生成Token失败 - 频道名为空, channelName: {}, requestId: {}",
                    request.getChannelName(), request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10004", "频道名为空", request.getRequestId()));
        }

        try {
            String token = tokenService.generateToken(request);
            Map<String, Object> response = new HashMap<>();
            response.put("appId", tokenService.getAppId());
            response.put("token", token);
            response.put("channel_name", request.getChannelName());
            response.put("uid", request.getUid());

            logger.info("生成Token请求完成 - requestId: {}", request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(response, request.getRequestId()));
        } catch (Exception e) {
            logger.error("生成Token失败 - requestId: {}, error: {}", request.getRequestId(), e.getMessage());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10005", "生成Token失败", request.getRequestId()));
        }
    }

    /**
     * 获取向量文档预设列表接口
     *
     * 返回系统预设的向量文档列表
     * 对应Go版本的handlerVectorDocumentPresetList方法
     *
     * @return 向量文档预设列表
     */
    @GetMapping("/vector/document/preset/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVectorDocumentPresetList() {
        List<Map<String, Object>> presetList = new ArrayList<>();
        String vectorDocumentPresetList = System.getenv("VECTOR_DOCUMENT_PRESET_LIST");

        if (vectorDocumentPresetList != null && !vectorDocumentPresetList.isEmpty()) {
            try {
                presetList = objectMapper.readValue(vectorDocumentPresetList, List.class);
            } catch (Exception e) {
                logger.error("解析向量文档预设列表JSON失败", e);
                return ResponseEntity.ok().body(ApiResponse.error("10007", "解析JSON失败"));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(presetList));
    }

    /**
     * 更新向量文档接口
     *
     * 更新指定频道的向量文档配置
     * 对应Go版本的handlerVectorDocumentUpdate方法
     *
     * @param request 向量文档更新请求对象
     * @return 更新结果
     */
    @PostMapping("/vector/document/update")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateVectorDocument(
            @RequestBody VectorDocumentUpdate request) {
        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("更新向量文档失败 - 频道名为空, channelName: {}, requestId: {}",
                    request.getChannelName(), request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10004", "频道名为空", request.getRequestId()));
        }

        if (!workerService.containsWorker(request.getChannelName())) {
            logger.error("更新向量文档失败 - 频道不存在, channelName: {}, requestId: {}",
                    request.getChannelName(), request.getRequestId());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10002", "频道不存在", request.getRequestId()));
        }

        logger.info("更新向量文档请求开始 - channelName: {}, requestId: {}", request.getChannelName(),
                request.getRequestId());

        try {
            // 更新Worker
            Worker worker = workerService.getWorker(request.getChannelName());
            WorkerUpdateReq updateReq = new WorkerUpdateReq();
            updateReq.setRequestId(request.getRequestId());
            updateReq.setChannelName(request.getChannelName());
            updateReq.setCollection(request.getCollection());
            updateReq.setFileName(request.getFileName());

            WorkerUpdateReq.WorkerUpdateReqTen ten = new WorkerUpdateReq.WorkerUpdateReqTen();
            ten.setName("update_querying_collection");
            ten.setType("cmd");
            updateReq.setTen(ten);

            workerService.updateWorker(updateReq);

            Map<String, Object> response = new HashMap<>();
            response.put("channel_name", request.getChannelName());

            logger.info(
                    "更新向量文档请求完成 - channelName: {}, Collection: {}, FileName: {}, requestId: {}",
                    request.getChannelName(), request.getCollection(), request.getFileName(), request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(response, request.getRequestId()));
        } catch (Exception e) {
            logger.error(
                    "更新向量文档失败 - channelName: {}, Collection: {}, FileName: {}, requestId: {}, error: {}",
                    request.getChannelName(), request.getCollection(), request.getFileName(), request.getRequestId(),
                    e.getMessage());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10104", "更新Worker失败", request.getRequestId()));
        }
    }

    /**
     * 上传向量文档接口
     *
     * 上传向量文档文件并更新Worker配置
     * 对应Go版本的handlerVectorDocumentUpload方法
     *
     * @param requestId   请求ID
     * @param channelName 频道名称
     * @param file        上传的文件
     * @return 上传结果
     */
    @PostMapping("/vector/document/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadVectorDocument(
            @RequestParam("request_id") String requestId,
            @RequestParam("channel_name") String channelName,
            @RequestParam("file") MultipartFile file) {
        if (channelName == null || channelName.trim().isEmpty()) {
            logger.error("上传向量文档失败 - 频道名为空, channelName: {}, requestId: {}", channelName,
                    requestId);
            return ResponseEntity.ok().body(ApiResponse.error("10004", "频道名为空", requestId));
        }

        if (!workerService.containsWorker(channelName)) {
            logger.error("上传向量文档失败 - 频道不存在, channelName: {}, requestId: {}",
                    channelName, requestId);
            return ResponseEntity.ok().body(ApiResponse.error("10002", "频道不存在", requestId));
        }

        logger.info("上传向量文档请求开始 - channelName: {}, requestId: {}", channelName, requestId);

        try {
            // 保存上传的文件
            String uploadFile = String.format("%s/file-%s-%d%s", serverConfig.getLogPath(),
                    md5(channelName), System.nanoTime(), getFileExtension(file.getOriginalFilename()));

            File destFile = new File(uploadFile);
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }
            file.transferTo(destFile);

            // 生成集合名称
            String collection = String.format("a%s_%d", md5(channelName), System.nanoTime());
            String fileName = getFileName(file.getOriginalFilename());

            // 更新Worker
            WorkerUpdateReq updateReq = new WorkerUpdateReq();
            updateReq.setRequestId(requestId);
            updateReq.setChannelName(channelName);
            updateReq.setCollection(collection);
            updateReq.setFileName(fileName);

            WorkerUpdateReq.WorkerUpdateReqTen ten = new WorkerUpdateReq.WorkerUpdateReqTen();
            ten.setName("update_querying_collection");
            ten.setType("cmd");
            updateReq.setTen(ten);

            workerService.updateWorker(updateReq);

            Map<String, Object> response = new HashMap<>();
            response.put("channel_name", channelName);
            response.put("collection", collection);
            response.put("file_name", fileName);

            logger.info(
                    "上传向量文档请求完成 - channelName: {}, Collection: {}, FileName: {}, requestId: {}",
                    channelName, collection, fileName, requestId);
            return ResponseEntity.ok(ApiResponse.success(response, requestId));
        } catch (Exception e) {
            logger.error(
                    "上传向量文档失败 - channelName: {}, requestId: {}, error: {}",
                    channelName, requestId, e.getMessage());
            return ResponseEntity.ok()
                    .body(ApiResponse.error("10006", "保存文件失败", requestId));
        }
    }

    // ==================== 工具方法 ====================
    /**
     * 计算字符串的MD5哈希值
     *
     * @param input 输入字符串
     * @return MD5哈希值
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("计算MD5失败 - input: {}, error: {}", input, e.getMessage());
            return "";
        }
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
    }

    /**
     * 获取文件名（不含扩展名）
     *
     * @param filename 文件名
     * @return 不含扩展名的文件名
     */
    private String getFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(0, lastDotIndex) : filename;
    }
}