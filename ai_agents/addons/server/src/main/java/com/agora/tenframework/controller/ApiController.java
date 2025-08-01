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
 * API Controller - Complete implementation based on Go code
 *
 * @author Agora IO
 * @version 1.0.0
 */
@RestController
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    private WorkerService workerService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ServerConfig serverConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Health check endpoint - equivalent to Go's handlerHealth
     */
    @GetMapping("/")
    public ResponseEntity<ApiResponse<Void>> health() {
        logger.debug("handlerHealth");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> healthCheck() {
        logger.debug("handlerHealth");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * List workers endpoint - equivalent to Go's handlerList
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listWorkers() {
        logger.info("handlerList start");

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Worker worker : workerService.getAllWorkers()) {
            Map<String, Object> workerJson = new HashMap<>();
            workerJson.put("channelName", worker.getChannelName());
            workerJson.put("createTs", worker.getCreateTs());
            filtered.add(workerJson);
        }

        logger.info("handlerList end");
        return ResponseEntity.ok(ApiResponse.success(filtered));
    }

    /**
     * Get graphs endpoint - equivalent to Go's handleGraphs
     */
    @GetMapping("/graphs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGraphs() {
        try {
            // Read the property.json file and get the graph list from predefined_graphs
            String content = new String(Files.readAllBytes(Paths.get(Constants.PROPERTY_JSON_FILE)));
            Map<String, Object> propertyJson = objectMapper.readValue(content,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> tenSection = (Map<String, Object>) propertyJson.get("ten");
            if (tenSection == null) {
                logger.error("Invalid format: _ten section missing");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10100", "Invalid format: _ten section missing"));
            }

            List<Object> predefinedGraphs = (List<Object>) tenSection.get("predefined_graphs");
            if (predefinedGraphs == null) {
                logger.error("Invalid format: predefined_graphs missing or not an array");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10100", "Invalid format: predefined_graphs missing or not an array"));
            }

            // Filter the graph with the matching name
            List<Map<String, Object>> graphs = new ArrayList<>();
            for (Object graph : predefinedGraphs) {
                Map<String, Object> graphMap = (Map<String, Object>) graph;
                Map<String, Object> filteredGraph = new HashMap<>();
                filteredGraph.put("name", graphMap.get("name"));
                filteredGraph.put("uuid", graphMap.get("name"));
                filteredGraph.put("auto_start", graphMap.get("auto_start"));
                graphs.add(filteredGraph);
            }

            return ResponseEntity.ok(ApiResponse.success(graphs));
        } catch (Exception e) {
            logger.error("failed to read property.json file", e);
            return ResponseEntity.status(500).body(ApiResponse.error("10106", "Failed to read property.json file"));
        }
    }

    /**
     * Get addon default properties endpoint - equivalent to Go's
     * handleAddonDefaultProperties
     */
    @GetMapping("/dev-tmp/addons/default-properties")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAddonDefaultProperties() {
        try {
            // Get the base directory path
            String baseDir = "./agents/ten_packages/extension";

            // Read all folders under the base directory
            File baseDirFile = new File(baseDir);
            if (!baseDirFile.exists() || !baseDirFile.isDirectory()) {
                logger.error("failed to read extension directory - Directory not found");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10105", "Failed to read extension directory"));
            }

            File[] entries = baseDirFile.listFiles();
            if (entries == null) {
                logger.error("failed to read extension directory - Cannot list files");
                return ResponseEntity.status(500)
                        .body(ApiResponse.error("10105", "Failed to read extension directory"));
            }

            // Iterate through each folder and read the property.json file
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
                        logger.warn("failed to read property file - addon: {}, err: {}", addonName, e.getMessage());
                        continue;
                    }
                }
            }

            return ResponseEntity.ok(ApiResponse.success(addons));
        } catch (Exception e) {
            logger.error("failed to read extension directory", e);
            return ResponseEntity.status(500).body(ApiResponse.error("10105", "Failed to read extension directory"));
        }
    }

    /**
     * Ping endpoint - equivalent to Go's handlerPing
     */
    @PostMapping("/ping")
    public ResponseEntity<ApiResponse<Void>> ping(@RequestBody PingRequest request) {
        logger.info("handlerPing start - channelName: {}, requestId: {}", request.getChannelName(),
                request.getRequestId());

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("handlerPing channel empty - channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10004", "channel empty", request.getRequestId()));
        }

        if (!workerService.containsWorker(request.getChannelName())) {
            logger.error("handlerPing channel not existed - channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10002", "channel not existed", request.getRequestId()));
        }

        // Update worker
        Worker worker = workerService.getWorker(request.getChannelName());
        worker.setUpdateTs(System.currentTimeMillis() / 1000);

        logger.info("handlerPing end - worker: {}, requestId: {}", worker, request.getRequestId());
        return ResponseEntity.ok(ApiResponse.success(null, request.getRequestId()));
    }

    /**
     * Start worker endpoint - equivalent to Go's handlerStart
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Void>> startWorker(@RequestBody StartRequest request) {
        int workersRunning = workerService.getWorkersSize();

        logger.info("handlerStart start - workersRunning: {}", workersRunning);

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("handlerStart channel empty - channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10004", "channel empty", request.getRequestId()));
        }

        if (workersRunning >= serverConfig.getWorkersMax()) {
            logger.error("handlerStart workers exceed - workersRunning: {}, WorkersMax: {}, requestId: {}",
                    workersRunning, serverConfig.getWorkersMax(), request.getRequestId());
            return ResponseEntity.status(429).body(ApiResponse.error("10001", "workers limit", request.getRequestId()));
        }

        if (workerService.containsWorker(request.getChannelName())) {
            logger.error("handlerStart channel existed - channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10003", "channel existed", request.getRequestId()));
        }

        // Check if the graphName contains "gemini"
        if (request.getGraphName() != null && request.getGraphName().contains("gemini")) {
            // Count existing workers with the same graphName
            int graphNameCount = 0;
            for (Worker worker : workerService.getAllWorkers()) {
                if (request.getGraphName().equals(worker.getGraphName())) {
                    graphNameCount++;
                }
            }

            // Reject if more than 3 workers are using the same graphName
            if (graphNameCount >= Constants.MAX_GEMINI_WORKER_COUNT) {
                logger.error("handlerStart graphName workers exceed limit - graphName: {}, graphNameCount: {}",
                        request.getGraphName(), graphNameCount);
                return ResponseEntity.status(429)
                        .body(ApiResponse.error("10001", "workers limit", request.getRequestId()));
            }
        }

        try {
            Worker worker = workerService.startWorker(request);
            logger.info("handlerStart end - workersRunning: {}, worker: {}, requestId: {}",
                    workerService.getWorkersSize(), worker, request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(null, request.getRequestId()));
        } catch (Exception e) {
            logger.error("handlerStart start worker failed - requestId: {}", request.getRequestId(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("10101", "start worker failed", request.getRequestId()));
        }
    }

    /**
     * Stop worker endpoint - equivalent to Go's handlerStop
     */
    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<Void>> stopWorker(@RequestBody StopRequest request) {
        logger.info("handlerStop start - req: {}", request);

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("handlerStop channel empty - channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10004", "channel empty", request.getRequestId()));
        }

        if (!workerService.containsWorker(request.getChannelName())) {
            logger.error("handlerStop channel not existed - channelName: {}, requestId: {}", request.getChannelName(),
                    request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10002", "channel not existed", request.getRequestId()));
        }

        try {
            workerService.stopWorker(request.getChannelName(), request.getRequestId());
            logger.info("handlerStop end - requestId: {}", request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(null, request.getRequestId()));
        } catch (Exception e) {
            logger.error("handlerStop kill app failed - worker: {}, requestId: {}",
                    workerService.getWorker(request.getChannelName()), request.getRequestId(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("10102", "stop worker failed", request.getRequestId()));
        }
    }

    /**
     * Generate token endpoint - equivalent to Go's handlerGenerateToken
     */
    @PostMapping("/token/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateToken(@RequestBody GenerateTokenRequest request) {
        logger.info("handlerGenerateToken start - req: {}", request);

        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("handlerGenerateToken channel empty - channelName: {}, requestId: {}",
                    request.getChannelName(), request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10004", "channel empty", request.getRequestId()));
        }

        if (serverConfig.getAppCertificate() == null || serverConfig.getAppCertificate().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("appId", serverConfig.getAppId());
            response.put("token", serverConfig.getAppId());
            response.put("channel_name", request.getChannelName());
            response.put("uid", request.getUid());
            return ResponseEntity.ok(ApiResponse.success(response, request.getRequestId()));
        }

        try {
            String token = tokenService.generateToken(request);
            Map<String, Object> response = new HashMap<>();
            response.put("appId", serverConfig.getAppId());
            response.put("token", token);
            response.put("channel_name", request.getChannelName());
            response.put("uid", request.getUid());

            logger.info("handlerGenerateToken end - requestId: {}", request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(response, request.getRequestId()));
        } catch (Exception e) {
            logger.error("handlerGenerateToken generate token failed - requestId: {}", request.getRequestId(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10005", "generate token failed", request.getRequestId()));
        }
    }

    /**
     * Vector document preset list endpoint - equivalent to Go's
     * handlerVectorDocumentPresetList
     */
    @GetMapping("/vector/document/preset/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVectorDocumentPresetList() {
        List<Map<String, Object>> presetList = new ArrayList<>();
        String vectorDocumentPresetList = System.getenv("VECTOR_DOCUMENT_PRESET_LIST");

        if (vectorDocumentPresetList != null && !vectorDocumentPresetList.isEmpty()) {
            try {
                presetList = objectMapper.readValue(vectorDocumentPresetList, List.class);
            } catch (Exception e) {
                logger.error("handlerVectorDocumentPresetList parse json failed", e);
                return ResponseEntity.badRequest().body(ApiResponse.error("10007", "parse json failed"));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(presetList));
    }

    /**
     * Vector document update endpoint - equivalent to Go's
     * handlerVectorDocumentUpdate
     */
    @PostMapping("/vector/document/update")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateVectorDocument(
            @RequestBody VectorDocumentUpdate request) {
        if (request.getChannelName() == null || request.getChannelName().trim().isEmpty()) {
            logger.error("handlerVectorDocumentUpdate channel empty - channelName: {}, requestId: {}",
                    request.getChannelName(), request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10004", "channel empty", request.getRequestId()));
        }

        if (!workerService.containsWorker(request.getChannelName())) {
            logger.error("handlerVectorDocumentUpdate channel not existed - channelName: {}, requestId: {}",
                    request.getChannelName(), request.getRequestId());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10002", "channel not existed", request.getRequestId()));
        }

        logger.info("handlerVectorDocumentUpdate start - channelName: {}, requestId: {}", request.getChannelName(),
                request.getRequestId());

        try {
            // Update worker
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
                    "handlerVectorDocumentUpdate end - channelName: {}, Collection: {}, FileName: {}, requestId: {}",
                    request.getChannelName(), request.getCollection(), request.getFileName(), request.getRequestId());
            return ResponseEntity.ok(ApiResponse.success(response, request.getRequestId()));
        } catch (Exception e) {
            logger.error(
                    "handlerVectorDocumentUpdate update worker failed - channelName: {}, Collection: {}, FileName: {}, requestId: {}",
                    request.getChannelName(), request.getCollection(), request.getFileName(), request.getRequestId(),
                    e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("10104", "update worker failed", request.getRequestId()));
        }
    }

    /**
     * Vector document upload endpoint - equivalent to Go's
     * handlerVectorDocumentUpload
     */
    @PostMapping("/vector/document/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadVectorDocument(
            @RequestParam("request_id") String requestId,
            @RequestParam("channel_name") String channelName,
            @RequestParam("file") MultipartFile file) {
        if (channelName == null || channelName.trim().isEmpty()) {
            logger.error("handlerVectorDocumentUpload channel empty - channelName: {}, requestId: {}", channelName,
                    requestId);
            return ResponseEntity.badRequest().body(ApiResponse.error("10004", "channel empty", requestId));
        }

        if (!workerService.containsWorker(channelName)) {
            logger.error("handlerVectorDocumentUpload channel not existed - channelName: {}, requestId: {}",
                    channelName, requestId);
            return ResponseEntity.badRequest().body(ApiResponse.error("10002", "channel not existed", requestId));
        }

        logger.info("handlerVectorDocumentUpload start - channelName: {}, requestId: {}", channelName, requestId);

        try {
            // Save uploaded file
            String uploadFile = String.format("%s/file-%s-%d%s", serverConfig.getLogPath(),
                    md5(channelName), System.nanoTime(), getFileExtension(file.getOriginalFilename()));

            File destFile = new File(uploadFile);
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }
            file.transferTo(destFile);

            // Generate collection
            String collection = String.format("a%s_%d", md5(channelName), System.nanoTime());
            String fileName = getFileName(file.getOriginalFilename());

            // Update worker
            Worker worker = workerService.getWorker(channelName);
            WorkerUpdateReq updateReq = new WorkerUpdateReq();
            updateReq.setRequestId(requestId);
            updateReq.setChannelName(channelName);
            updateReq.setCollection(collection);
            updateReq.setFileName(fileName);
            updateReq.setPath(uploadFile);

            WorkerUpdateReq.WorkerUpdateReqTen ten = new WorkerUpdateReq.WorkerUpdateReqTen();
            ten.setName("file_chunk");
            ten.setType("cmd");
            updateReq.setTen(ten);

            workerService.updateWorker(updateReq);

            Map<String, Object> response = new HashMap<>();
            response.put("channel_name", channelName);
            response.put("collection", collection);
            response.put("file_name", fileName);

            logger.info(
                    "handlerVectorDocumentUpload end - channelName: {}, collection: {}, uploadFile: {}, requestId: {}",
                    channelName, collection, uploadFile, requestId);
            return ResponseEntity.ok(ApiResponse.success(response, requestId));
        } catch (Exception e) {
            logger.error("handlerVectorDocumentUpload save file failed - channelName: {}, requestId: {}", channelName,
                    requestId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("10006", "save file failed", requestId));
        }
    }

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
            return input;
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null)
            return "";
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
    }

    private String getFileName(String filename) {
        if (filename == null)
            return "";
        int lastSlashIndex = filename.lastIndexOf('/');
        int lastBackslashIndex = filename.lastIndexOf('\\');
        int lastSeparatorIndex = Math.max(lastSlashIndex, lastBackslashIndex);
        return lastSeparatorIndex >= 0 ? filename.substring(lastSeparatorIndex + 1) : filename;
    }
}