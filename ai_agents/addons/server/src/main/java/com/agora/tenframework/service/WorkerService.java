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
 * Worker Service - Complete implementation based on Go code
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

    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final AtomicInteger httpServerPort = new AtomicInteger(10000);

    private static final int HTTP_SERVER_PORT_MAX = 30000;
    private static final int HTTP_SERVER_PORT_MIN = 10000;

    private static final String WORKER_EXEC = "/app/agents/bin/start";
    private static final String WORKER_HTTP_SERVER_URL = "http://127.0.0.1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get next available HTTP server port
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
     * Create new worker - equivalent to Go's newWorker function
     */
    public Worker newWorker(String channelName, String logFile, boolean log2Stdout, String propertyJsonFile) {
        Worker worker = new Worker();
        worker.setChannelName(channelName);
        worker.setLogFile(logFile);
        worker.setLog2Stdout(log2Stdout);
        worker.setPropertyJsonFile(propertyJsonFile);
        worker.setCreateTs(System.currentTimeMillis() / 1000);
        worker.setUpdateTs(System.currentTimeMillis() / 1000);
        worker.setQuitTimeoutSeconds(60); // Default value like Go's newWorker function
        return worker;
    }

    /**
     * Start worker - equivalent to Go's worker.start method
     */
    public Worker startWorker(StartRequest request) throws Exception {
        String channelName = request.getChannelName();

        // Set worker HTTP server port - equivalent to Go's getHttpServerPort()
        request.setWorkerHttpServerPort(getNextHttpServerPort());

        // Process property file - equivalent to Go's processProperty method
        String[] result = processProperty(request);
        String propertyJsonFile = result[0];
        String logFile = result[1];

        // Create worker
        Worker worker = newWorker(channelName, logFile, serverConfig.isLog2Stdout(), propertyJsonFile);
        worker.setHttpServerPort(request.getWorkerHttpServerPort());
        worker.setGraphName(request.getGraphName());

        if (request.getQuitTimeoutSeconds() > 0) {
            worker.setQuitTimeoutSeconds(request.getQuitTimeoutSeconds());
        } else {
            worker.setQuitTimeoutSeconds(serverConfig.getWorkerQuitTimeoutSeconds());
        }

        // Start worker process - equivalent to Go's worker.start(&req)
        startWorkerProcess(worker, request);

        // Add worker to map only after successful start - equivalent to Go's
        // workers.SetIfNotExist
        // Note: Go's SetIfNotExist only sets if key doesn't exist, but we use
        // putIfAbsent for consistency
        workers.putIfAbsent(channelName, worker);
        worker.setUpdateTs(System.currentTimeMillis() / 1000);

        logger.info("Started worker for channel - channelName: {}, pid: {}, port: {}", channelName, worker.getPid(),
                worker.getHttpServerPort());

        return worker;
    }

    /**
     * Stop worker - equivalent to Go's worker.stop method
     */
    public void stopWorker(String channelName, String requestId) throws Exception {
        Worker worker = workers.get(channelName);

        if (worker == null) {
            throw new RuntimeException("Worker not found for channel: " + channelName);
        }

        logger.info("Worker stop start - channelName: {}, requestId: {}, pid: {}", channelName, requestId,
                worker.getPid());

        // Kill process
        if (worker.getPid() != null) {
            killProcess(worker.getPid());
        }

        // Remove worker - equivalent to Go's workers.Remove(channelName)
        workers.remove(channelName);

        logger.info("Worker stop end - channelName: {}, worker: {}, requestId: {}", channelName, worker, requestId);
    }

    /**
     * Update worker - equivalent to Go's worker.update method
     */
    public void updateWorker(WorkerUpdateReq request) throws Exception {
        String channelName = request.getChannelName();
        Worker worker = workers.get(channelName);

        if (worker == null) {
            throw new RuntimeException("Worker not found for channel: " + channelName);
        }

        logger.info("Worker update start - channelName: {}, requestId: {}", channelName, request.getRequestId());

        // Send HTTP request to worker - equivalent to Go's worker.update method
        String url = String.format("%s:%d/cmd", WORKER_HTTP_SERVER_URL, worker.getHttpServerPort());

        try {
            // Create HTTP client and send request - equivalent to Go's resty client
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
                throw new RuntimeException(String.format("HTTP status not OK, status: %d", response.statusCode()));
            }

            logger.info("Worker update end - channelName: {}, worker: {}, requestId: {}",
                    channelName, worker, request.getRequestId());
        } catch (Exception e) {
            logger.error("Worker update error - err: {}, channelName: {}, requestId: {}",
                    e.getMessage(), channelName, request.getRequestId());
            throw e;
        }
    }

    /**
     * Get all workers
     */
    public List<Worker> getAllWorkers() {
        return new ArrayList<>(workers.values());
    }

    /**
     * Get worker by channel name
     */
    public Worker getWorker(String channelName) {
        return workers.get(channelName);
    }

    /**
     * Check if worker exists
     */
    public boolean containsWorker(String channelName) {
        return workers.containsKey(channelName);
    }

    /**
     * Get workers size
     */
    public int getWorkersSize() {
        return workers.size();
    }

    /**
     * Clean up all workers
     */
    public void cleanAllWorkers() {
        logger.info("Cleaning up all workers");

        // Stop all workers - equivalent to Go's CleanWorkers logic
        for (Worker worker : workers.values()) {
            try {
                stopWorker(worker.getChannelName(), java.util.UUID.randomUUID().toString());
                logger.info("Worker cleanWorker success - channelName: {}, worker: {}", worker.getChannelName(),
                        worker);
            } catch (Exception e) {
                logger.error("Worker cleanWorker failed - channelName: {}, worker: {}", worker.getChannelName(), worker,
                        e);
            }
        }

        // Get running processes with the specific command pattern
        Set<Integer> runningPIDs = getRunningWorkerPIDs();

        // Create maps for easy lookup
        Map<Integer, Worker> workerMap = new HashMap<>();
        for (Worker worker : workers.values()) {
            if (worker.getPid() != null) {
                workerMap.put(worker.getPid(), worker);
            }
        }

        // Kill processes that are running but not in the workers list
        for (Integer pid : runningPIDs) {
            if (!workerMap.containsKey(pid)) {
                logger.info("Killing redundant process - pid: {}", pid);
                killProcess(pid);
            }
        }

        workers.clear();
    }

    /**
     * Process property file - equivalent to Go's processProperty method
     */
    private String[] processProperty(StartRequest request) throws Exception {
        // Read property.json file
        String content = new String(Files.readAllBytes(Paths.get(Constants.PROPERTY_JSON_FILE)));
        Map<String, Object> propertyJson = objectMapper.readValue(content,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });

        // Get graph name
        String graphName = request.getGraphName();
        if (graphName == null || graphName.isEmpty()) {
            throw new RuntimeException("graph_name is mandatory");
        }

        // Generate token - equivalent to Go's token generation
        String token = serverConfig.getAppId();
        if (serverConfig.getAppCertificate() != null && !serverConfig.getAppCertificate().isEmpty()) {
            try {
                // Use the same logic as Go's BuildTokenWithRtm
                GenerateTokenRequest tokenRequest = new GenerateTokenRequest();
                tokenRequest.setChannelName(request.getChannelName());
                tokenRequest.setUid(0L); // Use 0 as default UID for worker
                token = tokenService.generateToken(tokenRequest);
            } catch (Exception e) {
                logger.error("handlerStart generate token failed - requestId: {}", request.getRequestId(), e);
                throw new RuntimeException("generate token failed", e);
            }
        }
        request.setToken(token);

        // Locate the predefined graphs array
        Map<String, Object> tenSection = (Map<String, Object>) propertyJson.get("ten");
        if (tenSection == null) {
            throw new RuntimeException("Invalid format: _ten section missing");
        }

        List<Object> predefinedGraphs = (List<Object>) tenSection.get("predefined_graphs");
        if (predefinedGraphs == null) {
            throw new RuntimeException("Invalid format: predefined_graphs missing or not an array");
        }

        // Filter the graph with the matching name
        List<Object> newGraphs = new ArrayList<>();
        for (Object graph : predefinedGraphs) {
            Map<String, Object> graphMap = (Map<String, Object>) graph;
            if (graphName.equals(graphMap.get("name"))) {
                newGraphs.add(graph);
            }
        }

        if (newGraphs.isEmpty()) {
            throw new RuntimeException("Graph not found: " + graphName);
        }

        // Replace the predefined_graphs array with the filtered array
        tenSection.put("predefined_graphs", newGraphs);

        // Automatically start on launch
        for (Object graph : newGraphs) {
            Map<String, Object> graphMap = (Map<String, Object>) graph;
            graphMap.put("auto_start", true);
        }

        // Set additional properties to property.json
        if (request.getProperties() != null) {
            for (Map.Entry<String, Map<String, Object>> entry : request.getProperties().entrySet()) {
                String extensionName = entry.getKey();
                Map<String, Object> props = entry.getValue();

                if (extensionName != null && !extensionName.isEmpty()) {
                    for (Map.Entry<String, Object> propEntry : props.entrySet()) {
                        String prop = propEntry.getKey();
                        Object val = propEntry.getValue();

                        // Set each property to the appropriate graph and property
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

        // Set start parameters to property.json
        setStartParameters(newGraphs, request);

        // Validate environment variables in the "nodes" section
        validateEnvironmentVariables(newGraphs);

        // Marshal the modified JSON back to a string - use formatted JSON like Go's
        // json.MarshalIndent
        String modifiedPropertyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(propertyJson);

        // Generate timestamp - use the same format as Go: "20060102_150405_000"
        String ts = String.format("%tY%<tm%<td_%<tH%<tM%<tS_000", new Date());
        String propertyJsonFile = String.format("%s/property-%s-%s.json",
                serverConfig.getLogPath(), java.net.URLEncoder.encode(request.getChannelName(), "UTF-8"), ts);
        String logFile = String.format("%s/app-%s-%s.log",
                serverConfig.getLogPath(), java.net.URLEncoder.encode(request.getChannelName(), "UTF-8"), ts);

        // Write file
        Files.write(Paths.get(propertyJsonFile), modifiedPropertyJson.getBytes());

        return new String[] { propertyJsonFile, logFile };
    }

    /**
     * Set start parameters - equivalent to Go's startPropMap logic
     */
    private void setStartParameters(List<Object> graphs, StartRequest request) {
        // Use the same logic as Go's startPropMap
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
     * Set property in graphs
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
     * Validate environment variables - equivalent to Go's environment variable
     * validation
     */
    private void validateEnvironmentVariables(List<Object> graphs) {
        Pattern envPattern = Pattern.compile("\\$\\{env:([^}|]+)\\}");

        for (Object graph : graphs) {
            Map<String, Object> graphMap = (Map<String, Object>) graph;
            Map<String, Object> graphData = (Map<String, Object>) graphMap.get("graph");
            List<Object> nodes = (List<Object>) graphData.get("nodes");

            if (nodes == null) {
                logger.info("No nodes section in the graph");
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
                            boolean exists = System.getenv(variable) != null;
                            if (!exists) {
                                logger.error("Environment variable not found - variable: {}, property: {}", variable,
                                        key);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Start worker process - equivalent to Go's worker.start method
     */
    private void startWorkerProcess(Worker worker, StartRequest request) throws Exception {
        String shell = String.format("cd /app/agents && %s --property %s", WORKER_EXEC, worker.getPropertyJsonFile());

        logger.info("Worker start - requestId: {}, shell: {}", request.getRequestId(), shell);

        // Create process with proper setup - equivalent to Go's exec.Command with
        // Setpgid
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", shell);

        // Set up output handling based on Log2Stdout setting - equivalent to Go's log
        // file handling
        File logFile = null;
        if (!worker.isLog2Stdout()) {
            // Create log file with proper permissions - equivalent to Go's os.OpenFile
            logFile = new File(worker.getLogFile());
            logFile.getParentFile().mkdirs(); // Ensure directory exists
        }

        // Start the process
        Process process = pb.start();

        // Get process ID with retry logic - equivalent to Go's pgrep logic
        int pid = getProcessIdWithRetry(process, request.getRequestId());
        worker.setPid(pid);

        logger.info("Worker started - pid: {}, channel: {}", pid, worker.getChannelName());

        // Start output reader with initial prefix "-" - equivalent to Go's PrefixWriter
        startOutputReaderWithPrefix(process, "-", logFile);

        // Update the prefix with the actual channel name - equivalent to Go's prefix
        // update
        updateOutputReaderPrefix(process, worker.getChannelName());

        // Monitor process in background thread - equivalent to Go's goroutine
        monitorProcess(process, worker, request.getRequestId(), logFile);
    }

    // PrefixWriter equivalent - stores prefix and writer
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

    // Store both stdout and stderr PrefixWriters for each process
    private final Map<Process, Map<String, PrefixWriter>> prefixWriters = new ConcurrentHashMap<>();

    /**
     * Start output reader with prefix - equivalent to Go's PrefixWriter for both
     * stdout and stderr
     */
    private void startOutputReaderWithPrefix(Process process, String initialPrefix, File logFile) {
        // Set up output stream based on Log2Stdout setting
        OutputStream outputStream = System.out; // Default to stdout
        if (logFile != null) {
            try {
                outputStream = new FileOutputStream(logFile, true); // Append mode
            } catch (IOException e) {
                logger.error("Failed to open log file - logFile: {}", logFile, e);
                outputStream = System.out; // Fallback to stdout
            }
        }

        // Create PrefixWriter instances for both stdout and stderr - equivalent to Go's
        // PrefixWriter
        PrefixWriter stdoutPrefixWriter = new PrefixWriter(initialPrefix, new OutputStreamWriter(outputStream));
        PrefixWriter stderrPrefixWriter = new PrefixWriter(initialPrefix, new OutputStreamWriter(outputStream));

        // Store both PrefixWriters for this process
        Map<String, PrefixWriter> writers = new HashMap<>();
        writers.put("stdout", stdoutPrefixWriter);
        writers.put("stderr", stderrPrefixWriter);
        prefixWriters.put(process, writers);

        // Start stdout reader thread
        Thread stdoutReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Add prefix to each line - equivalent to Go's PrefixWriter
                    stdoutPrefixWriter.writeLine(line);

                    // Also log to our logger for debugging
                    logger.debug("Worker stdout - prefix: {}, line: {}", stdoutPrefixWriter.prefix, line);
                }
            } catch (IOException e) {
                logger.error("Error reading stdout for process", e);
            }
        });
        stdoutReaderThread.setDaemon(true);
        stdoutReaderThread.start();

        // Start stderr reader thread
        Thread stderrReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Add prefix to each line - equivalent to Go's PrefixWriter
                    stderrPrefixWriter.writeLine(line);

                    // Also log to our logger for debugging
                    logger.debug("Worker stderr - prefix: {}, line: {}", stderrPrefixWriter.prefix, line);
                }
            } catch (IOException e) {
                logger.error("Error reading stderr for process", e);
            } finally {
                prefixWriters.remove(process);
            }
        });
        stderrReaderThread.setDaemon(true);
        stderrReaderThread.start();
    }

    /**
     * Update the prefix with the actual channel name - equivalent to Go's prefix
     * update for both stdout and stderr
     */
    private void updateOutputReaderPrefix(Process process, String channelName) {
        Map<String, PrefixWriter> writers = prefixWriters.get(process);
        if (writers != null) {
            // Update both stdout and stderr PrefixWriters - equivalent to Go's prefix
            // update
            writers.get("stdout").setPrefix(channelName);
            writers.get("stderr").setPrefix(channelName);
            logger.debug("Updated output reader prefix - channelName: {}", channelName);
        }
    }

    /**
     * Get process ID with retry logic - equivalent to Go's pgrep logic
     */
    private int getProcessIdWithRetry(Process process, String requestId) {
        int pid = Math.toIntExact(process.pid());

        // Ensure the process has fully started - equivalent to Go's pgrep logic
        String shell = String.format("pgrep -P %d", pid);
        logger.info("Worker get pid - requestId: {}, shell: {}", requestId, shell);

        int subprocessPid = -1;
        for (int i = 0; i < 10; i++) { // retry for 10 times like Go
            try {
                Process pgrepProcess = Runtime.getRuntime().exec(new String[] { "sh", "-c", shell });
                String output = new String(pgrepProcess.getInputStream().readAllBytes()).trim();
                pgrepProcess.waitFor();

                if (!output.isEmpty()) {
                    subprocessPid = Integer.parseInt(output);
                    if (subprocessPid > 0 && isInProcessGroup(subprocessPid, pid)) {
                        break; // if pid is successfully obtained, exit loop
                    }
                }
            } catch (Exception e) {
                // Ignore errors and continue retrying
            }

            logger.warn("Worker get pid failed, retrying... - attempt: {}, pid: {}, subpid: {}, requestId: {}",
                    i + 1, pid, subprocessPid, requestId);
            try {
                Thread.sleep(1000); // wait for 1000ms like Go
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return pid;
            }
        }

        return pid;
    }

    /**
     * Monitor process in background - equivalent to Go's goroutine monitoring
     */
    private void monitorProcess(Process process, Worker worker, String requestId, File logFile) {
        Thread monitorThread = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    logger.error("Worker process failed - requestId: {}, exitCode: {}", requestId, exitCode);
                } else {
                    logger.info("Worker process completed successfully - requestId: {}", requestId);
                }
            } catch (InterruptedException e) {
                logger.error("Worker process monitoring interrupted - requestId: {}", requestId, e);
            } finally {
                // Close the log file when the command finishes - equivalent to Go's
                // logFile.Close()
                if (logFile != null) {
                    try {
                        // Note: In Java, the file is automatically closed when the process ends
                        // This is equivalent to Go's logFile.Close()
                    } catch (Exception e) {
                        logger.warn("Error closing log file - channelName: {}", worker.getChannelName(), e);
                    }
                }

                // Remove the worker from the map
                workers.remove(worker.getChannelName());
                logger.info("Worker removed from map - channelName: {}", worker.getChannelName());
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * Kill process - equivalent to Go's syscall.Kill
     * Go uses syscall.Kill(-w.Pid, syscall.SIGKILL) to kill the entire process
     * group
     */
    private void killProcess(int pid) {
        try {
            // Kill the entire process group (equivalent to Go's syscall.Kill(-w.Pid,
            // syscall.SIGKILL))
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", "kill -KILL -" + pid });
            process.waitFor();
            logger.info("Sent KILL signal to process group - pid: {}", pid);
        } catch (Exception e) {
            logger.error("Error killing process group - pid: {}", pid, e);
        }
    }

    /**
     * Generate log file path - equivalent to Go's log file generation
     */
    private String generateLogFilePath(String channelName) {
        // Use the same timestamp format as Go: "20060102_150405_000"
        String ts = String.format("%tY%<tm%<td_%<tH%<tM%<tS_000", new Date());
        return String.format("%s/app-%s-%s.log",
                serverConfig.getLogPath(),
                java.net.URLEncoder.encode(channelName, java.nio.charset.StandardCharsets.UTF_8), ts);
    }

    /**
     * URL encode - equivalent to Go's url.QueryEscape
     */
    private String urlEncode(String str) {
        try {
            return java.net.URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            return str;
        }
    }

    /**
     * Get field value from request object - equivalent to Go's getFieldValue
     */
    private Object getFieldValue(StartRequest request, String fieldName) {
        try {
            java.lang.reflect.Field field = StartRequest.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(request);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get running worker PIDs - equivalent to Go's getRunningWorkerPIDs function
     */
    private Set<Integer> getRunningWorkerPIDs() {
        Set<Integer> runningPIDs = new HashSet<>();
        try {
            // Define the command to find processes
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c",
                    "ps aux | grep \"bin/worker --property\" | grep -v grep" });

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length > 1) {
                        try {
                            int pid = Integer.parseInt(fields[1]); // PID is typically the second field
                            runningPIDs.add(pid);
                        } catch (NumberFormatException e) {
                            // Skip invalid PIDs
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get running worker PIDs", e);
        }
        return runningPIDs;
    }

    /**
     * Check if process is in process group - equivalent to Go's isInProcessGroup
     * function
     */
    private boolean isInProcessGroup(int pid, int pgid) {
        try {
            // Get the process group ID of the given PID
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", "ps -o pgid= -p " + pid });
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    int processPgid = Integer.parseInt(line.trim());
                    return processPgid == pgid;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to check process group - pid: {}, pgid: {}", pid, pgid, e);
        }
        return false;
    }
}