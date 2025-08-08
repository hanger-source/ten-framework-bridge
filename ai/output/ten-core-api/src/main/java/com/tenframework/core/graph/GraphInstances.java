package com.tenframework.core.graph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.util.ClientLocationUriUtils;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class GraphInstances {
    private final Map<String, GraphInstance> graphInstancesByClientLocationUri;
    private final Map<String, GraphInstance> graphInstancesByGraphId;
    private final Map<String, ClientConnectionExtension> clientConnectionExtensionMap;

    public GraphInstances() {
        graphInstancesByClientLocationUri = new ConcurrentHashMap<>();
        graphInstancesByGraphId = new ConcurrentHashMap<>();
        clientConnectionExtensionMap = new ConcurrentHashMap<>();
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
            // 删除图之前 保留最后的ClientConnectionExtension
            graphInstance.getExtension(ClientConnectionExtension.NAME).ifPresent(extension -> {
                clientConnectionExtensionMap.put(clientLocationUri, (ClientConnectionExtension)extension);
            });
            String graphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
            graphInstancesByGraphId.remove(graphId);
        }
        return graphInstance;
    }

    public ClientConnectionExtension getClientConnectionExtension(String clientLocationUri) {
        ClientConnectionExtension extension = clientConnectionExtensionMap.get(clientLocationUri);
        synchronized (extension) {
            extension.setOnClientConnectionStop(clientConnectionExtensionMap::remove);
        }
        return extension;
    }
}
