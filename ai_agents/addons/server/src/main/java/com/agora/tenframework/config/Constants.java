package com.agora.tenframework.config;

import com.agora.tenframework.model.Prop;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constants
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Constants {

    // Extension names
    public static final String EXTENSION_NAME_AGORA_RTC = "agora_rtc";
    public static final String EXTENSION_NAME_AGORA_RTM = "agora_rtm";
    public static final String EXTENSION_NAME_HTTP_SERVER = "http_server";

    // Property json file
    public static final String PROPERTY_JSON_FILE = "./agents/property.json";

    // Token expiration time
    public static final int TOKEN_EXPIRATION_IN_SECONDS = 86400;

    // Worker timeout infinity
    public static final int WORKER_TIMEOUT_INFINITY = -1;

    // Max Gemini worker count
    public static final int MAX_GEMINI_WORKER_COUNT = 3;

    // Start property map - equivalent to Go's startPropMap
    public static final Map<String, List<Prop>> START_PROP_MAP = new HashMap<>();

    static {
        // ChannelName mappings
        START_PROP_MAP.put("ChannelName", Arrays.asList(
                new Prop(EXTENSION_NAME_AGORA_RTC, "channel"),
                new Prop(EXTENSION_NAME_AGORA_RTM, "channel")));

        // RemoteStreamId mappings
        START_PROP_MAP.put("RemoteStreamId", Arrays.asList(
                new Prop(EXTENSION_NAME_AGORA_RTC, "remote_stream_id")));

        // BotStreamId mappings
        START_PROP_MAP.put("BotStreamId", Arrays.asList(
                new Prop(EXTENSION_NAME_AGORA_RTC, "stream_id")));

        // Token mappings
        START_PROP_MAP.put("Token", Arrays.asList(
                new Prop(EXTENSION_NAME_AGORA_RTC, "token"),
                new Prop(EXTENSION_NAME_AGORA_RTM, "token")));

        // WorkerHttpServerPort mappings
        START_PROP_MAP.put("WorkerHttpServerPort", Arrays.asList(
                new Prop(EXTENSION_NAME_HTTP_SERVER, "listen_port")));
    }
}