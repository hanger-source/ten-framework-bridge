package com.tenframework.core.graph;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.util.ClientLocationUriUtils;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_APP_URI;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_GRAPH_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public class GraphInstances {
    private final Map<String, GraphInstance> graphInstancesByClientLocationUri;
    private final Map<String, GraphInstance> graphInstancesByGraphId;
    private final GraphInstance systemGraphInstance;

    public GraphInstances(MessageSubmitter messageSubmitter) {
        graphInstancesByClientLocationUri = new ConcurrentHashMap<>();
        graphInstancesByGraphId = new ConcurrentHashMap<>();

        try {
            systemGraphInstance = new GraphInstance(MessageConstants.SYS_APP_URI,
                GraphLoader.loadGraphConfigFromClassPath("graph/system.json"),
                messageSubmitter);
            GraphLoader.loadGraphConfigFromClassPath("graph/system.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public GraphInstance getByGraphId(String sourceGraphId) {
        return graphInstancesByGraphId.get(sourceGraphId);
    }

    public GraphInstance getByClientLocationUri(String clientLocationUri) {
        return graphInstancesByClientLocationUri.get(clientLocationUri);
    }

    public void put(String clientLocationUri, GraphInstance graphInstance) {
        graphInstancesByClientLocationUri.put(clientLocationUri, graphInstance);
        String graphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
        graphInstancesByGraphId.put(graphId, graphInstance);
    }

    public GraphInstance remove(String clientLocationUri) {
        GraphInstance graphInstance = graphInstancesByClientLocationUri.remove(clientLocationUri);
        if (graphInstance != null) {
            String graphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
            graphInstancesByGraphId.remove(graphId);
        }
        return graphInstance;
    }

    public void registerGraph(String clientLocationUri, GraphInstance graphInstance)
        throws ClassNotFoundException, InstantiationException, IllegalAccessException {

        String clientAppUri = ClientLocationUriUtils.getAppUri(clientLocationUri);
        GraphConfig graphConfig = graphInstance.getGraphConfig();
        if (graphConfig.getNodes() != null) {
            for (NodeConfig nodeConfig : graphConfig.getNodes()) {
                String extensionName = nodeConfig.getName();
                String extensionType = nodeConfig.getType();
                Map<String, Object> nodeProperties = nodeConfig.getProperties() != null
                    ? nodeConfig.getProperties()
                    : new HashMap<>();
                Object instance = null;
                if (extensionType != null && !extensionType.isEmpty()) {
                    Class<?> clazz = Class.forName(extensionType);
                    instance = clazz.newInstance();
                }
                if (instance != null) {
                    Map<String, Object> clientConnectionProperties = new HashMap<>();
                    clientConnectionProperties.put(PROPERTY_CLIENT_LOCATION_URI,
                        clientLocationUri);
                    clientConnectionProperties.put(PROPERTY_CLIENT_APP_URI, clientAppUri);
                    clientConnectionProperties.put(PROPERTY_CLIENT_GRAPH_ID, graphInstance.getGraphId());
                    if (!(instance instanceof Extension extension)) {
                        throw new IllegalArgumentException(
                            "Extension class does not implement Extension interface: " + extensionType);
                    }
                    nodeProperties.putAll(clientConnectionProperties);
                    graphInstance.registerExtension(extensionName, extension, nodeProperties);
                    log.info("Extension已注册到图: graphId={}, extensionName={}, extensionType={}",
                        graphInstance.getGraphId(), extensionName, extensionType);
                }
            }
        }
        put(clientLocationUri, graphInstance);
    }
}
