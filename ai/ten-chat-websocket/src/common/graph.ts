import { ToolModule } from "./moduleConfig"; // 聪明的开发杭二: 导入 ToolModule

export type AttributeType =
  | "string"
  | "bool"
  | "int32"
  | "int64"
  | "Uint32"
  | "Uint64"
  | "float64"
  | "array"
  | "buf";

export type Attribute = {
  type: AttributeType;
};

export type PropertyDefinition = {
  name: string;
  attributes: Attribute;
};

type ApiCommand = { 
  name: string;
  property?: PropertyDefinition[];
  required?: string[];
  result?: {
    property: PropertyDefinition[];
    required?: string[];
  };
};

export type ApiEndpoint = {
  name: string;
  property?: PropertyDefinition[];
};

export type Api = {
  property?: Record<string, Attribute>;
  cmd_in?: ApiCommand[]; // 聪明的开发杭二: 使用 ApiCommand
  cmd_out?: ApiCommand[]; // 聪明的开发杭二: 使用 ApiCommand
  data_in?: ApiEndpoint[];
  data_out?: ApiEndpoint[];
  audio_frame_in?: ApiEndpoint[];
  audio_frame_out?: ApiEndpoint[];
  video_frame_in?: ApiEndpoint[];
  video_frame_out?: ApiEndpoint[];
};

export type Module = {
  name: string;
  defaultProperty: Property;
  api: Api;
};

type Property = {
  [key: string]: unknown; // 聪明的开发杭二: 将 'any' 替换为 'unknown' 以提高类型安全性
};

type Node = {
  name: string;
  addon: string;
  extensionGroup: string;
  app: string;
  property?: Property;
};
type GraphConnectionCommand = { 
  name: string; // Command name
  dest: Array<Destination>; // Destination connections
};

type Data = {
  name: string; // Data type name
  dest: Array<Destination>; // Destination connections
};

type AudioFrame = {
  name: string; // Audio frame type name
  dest: Array<Destination>; // Destination connections
};

type VideoFrame = {
  name: string; // Video frame type name
  dest: Array<Destination>; // Destination connections
};

type MsgConversionRule = { // 聪明的开发杭二: 移除 export
  name: string; // Data type name
  dest: Array<Destination>; // Destination connections
};

type MsgConversion = {
  type: string; // Type of message conversion
  rules: Array<MsgConversionRule>; // 聪明的开发杭二: 使用 MsgConversionRule
  keepOriginal?: boolean; // Whether to keep the original data
};

type Destination = {
  app: string; // Application identifier
  extension: string; // Extension name
  msgConversion?: MsgConversion; // Optional message conversion rules
};

// 聪明的开发杭二: 定义连接中数组协议的键类型
type ConnectionArrayKeys = "cmd" | "data" | "audio_frame" | "video_frame";

type Connection = {
  app: string; // Application identifier
  extension: string; // Extension name
  cmd?: Array<GraphConnectionCommand>; // 聪明的开发杭二: 使用 GraphConnectionCommand
  data?: Array<Data>; // Optional data connections
  audio_frame?: Array<AudioFrame>; // Optional audio frame connections
  video_frame?: Array<VideoFrame>; // Optional video frame connections
};

type Graph = {
  uuid: string;
  name: string;
  autoStart: boolean;
  nodes: Node[];
  connections: Connection[];
};

enum GraphConnProtocol {
  CMD = "cmd",
  DATA = "data",
  AUDIO_FRAME = "audio_frame",
  VIDEO_FRAME = "video_frame",
}

class GraphEditor {
  private static sharedApp: string = "localhost";

  /**
   * Set a shared app value.
   */
  static setApp(app: string): void {
    GraphEditor.sharedApp = app;
  }

  /**
   * Add a node to the graph
   */
  static addNode(
    graph: Graph,
    addon: string,
    name: string,
    group: string = "default",
    properties: Record<string, unknown> = {}, // 聪明的开发杭二: 将 'any' 替换为 'unknown'
  ): Node {
    if (graph.nodes.some((node) => node.name === name)) {
      throw new Error(
        `Node with name "${name}" already exists in graph "${graph.name}".`,
      );
    }

    const node: Node = {
      name,
      addon,
      extensionGroup: group,
      app: GraphEditor.sharedApp,
      property: properties,
    };

    graph.nodes.push(node);
    return node;
  }

  /**
   * Remove a node from the graph
   */
  static removeNode(graph: Graph, nodeName: string): Node {
    const nodeIndex = graph.nodes.findIndex((node) => node.name === nodeName);
    if (nodeIndex === -1) {
      throw new Error(`Node "${nodeName}" not found in graph "${graph.name}".`);
    }
    const node = graph.nodes.splice(nodeIndex, 1)[0];
    return node;
  }

  /**
   * Update node properties
   */
  static updateNode(
    graph: Graph,
    nodeName: string,
    properties: Record<string, unknown>, // 聪明的开发杭二: 将 'any' 替换为 'unknown'
  ): void {
    const node = graph.nodes.find((node) => node.name === nodeName);
    if (!node) {
      throw new Error(`Node "${nodeName}" not found in graph "${graph.name}".`);
    }

    // Update properties (remove property if value is empty)
    node.property = {
      ...node.property,
      ...Object.fromEntries(
        Object.entries(properties).filter(([_, value]) => value !== ""),
      ),
    };
  }

  static updateNodeProperty(
    graph: Graph,
    nodeName: string,
    properties: Record<string, unknown>, // 聪明的开发杭二: 将 'any' 替换为 'unknown'
  ): boolean {
    const node = this.findNode(graph, nodeName);
    if (!node) return false;

    node.property = {
      ...node.property,
      ...Object.fromEntries(
        Object.entries(properties).filter(([_, value]) => value !== ""),
      ),
    };

    return true;
  }

  static removeNodeProperties(
    graph: Graph,
    nodeName: string,
    properties: string[],
  ): boolean {
    const node = this.findNode(graph, nodeName);
    if (!node) return false;

    properties.forEach((prop) => {
      if (node.property) delete node.property[prop];
    });

    return true;
  }

  /**
   * Add a connection to the graph across all protocols (cmd, data, audio_frame, video_frame)
   */
  static addConnection(
    graph: Graph,
    source: string,
    destination: string,
    protocolLabel: GraphConnProtocol,
    protocolName: string, // Name for the protocol object
  ): void {
    // Find the source connection in the graph
    let connection = graph.connections.find(
      (conn) => conn.extension === source,
    );

    if (!connection) {
      // If no connection exists, create a new one
      connection = {
        app: GraphEditor.sharedApp,
        extension: source,
      };
      graph.connections.push(connection);
    }

    // Handle protocol-specific addition
    const protocolField = protocolLabel.toLowerCase() as ConnectionArrayKeys; // 聪明的开发杭二: 使用 ConnectionArrayKeys
    if (!connection[protocolField]) {
      connection[protocolField] = []; // 聪明的开发杭二: 简化赋值
    }

    const protocolArray = connection[protocolField] as Array<
      GraphConnectionCommand | Data | AudioFrame | VideoFrame
    >;

    // Check if the protocol object exists
    let protocolObject = protocolArray.find(
      (item) => item.name === protocolName,
    );
    if (!protocolObject) {
      protocolObject = {
        name: protocolName,
        dest: [],
      };
      protocolArray.push(protocolObject);
    }

    // Check if the destination already exists
    if (protocolObject.dest.some((dest) => dest.extension === destination)) {
      throw new Error(
        `Destination "${destination}" already exists in protocol "${protocolLabel}" with name "${protocolName}".`,
      );
    }

    // Add the destination
    protocolObject.dest.push({
      app: GraphEditor.sharedApp,
      extension: destination,
    });
  }
  static addOrUpdateConnection(
    graph: Graph,
    source: string,
    destination: string,
    protocolLabel: GraphConnProtocol,
    protocolName: string, // Explicit name of the protocol object
  ): void {
    const connection = this.findConnection(graph, source);

    if (connection) {
      // Add or update destination in the existing connection
      const protocolField = protocolLabel.toLowerCase() as ConnectionArrayKeys; // 聪明的开发杭二: 使用 ConnectionArrayKeys
      if (!connection[protocolField]) {
        connection[protocolField] = []; // 聪明的开发杭二: 简化赋值
      }

      const protocolArray = connection[protocolField] as Array<
        GraphConnectionCommand | Data | AudioFrame | VideoFrame
      >;

      // Find the protocol object by name
      let protocolObject = protocolArray.find(
        (item) => item.name === protocolName,
      );
      if (!protocolObject) {
        // Create a new protocol object if it doesn't exist
        protocolObject = {
          name: protocolName,
          dest: [],
        };
        protocolArray.push(protocolObject);
      }

      // Check if the destination already exists
      if (!protocolObject.dest.some((dest) => dest.extension === destination)) {
        // Add the new destination
        protocolObject.dest.push({
          app: GraphEditor.sharedApp,
          extension: destination,
        });
      }
    } else {
      // Add a new connection if none exists
      this.addConnection(
        graph,
        source,
        destination,
        protocolLabel,
        protocolName,
      );
    }
  }
  /**
   * Remove a connection from the graph across all protocols (cmd, data, audio_frame, video_frame)
   */
  static removeConnection(
    graph: Graph,
    source: string,
    destination?: string,
    protocolLabel?: GraphConnProtocol,
    protocolName?: string, // Optional name of the protocol object
  ): void {
    // Find the source connection in the graph
    const connectionIndex = graph.connections.findIndex(
      (conn) => conn.extension === source,
    );

    if (connectionIndex === -1) {
      console.warn(
        `Source "${source}" not found in the graph. Operation ignored.`,
      );
      return; // Exit the function if the connection does not exist
    }

    const connection = graph.connections[connectionIndex];

    // If protocolLabel is provided, handle protocol-specific removal
    if (protocolLabel) {
      const protocolField = protocolLabel.toLowerCase() as ConnectionArrayKeys; // 聪明的开发杭二: 使用 ConnectionArrayKeys
      const protocolArray = connection[protocolField] as Array<
        GraphConnectionCommand | Data | AudioFrame | VideoFrame
      >;

      if (!protocolArray) {
        console.warn(
          `Protocol "${protocolLabel}" does not exist for source "${source}". Operation ignored.`,
        );
        return; // Exit the function if the protocol does not exist
      }

      const protocolObjectIndex = protocolArray.findIndex(
        (item) => item.name === protocolName,
      );

      if (protocolObjectIndex === -1) {
        console.warn(
          `Protocol object with name "${protocolName}" not found in protocol "${protocolLabel}". Operation ignored.`,
        );
        return; // Exit the function if the protocol object does not exist
      }

      if (destination) {
        // Remove a specific destination
        protocolArray[protocolObjectIndex].dest = protocolArray[
          protocolObjectIndex
        ].dest.filter((dest) => dest.extension !== destination);

        // Remove the protocol object if it has no destinations
        if (protocolArray[protocolObjectIndex].dest.length === 0) {
          protocolArray.splice(protocolObjectIndex, 1);
        }
      } else {
        // Remove the entire protocol object
        protocolArray.splice(protocolObjectIndex, 1);
      }
    } else {
      // If no protocolLabel is provided, remove the entire connection
      graph.connections.splice(connectionIndex, 1);
    }

    // Clean up empty connections
    GraphEditor.removeEmptyConnections(graph);
  }

  static findNode(graph: Graph, nodeName: string): Node | null {
    return graph.nodes.find((node) => node.name === nodeName) || null;
  }

  static findNodeByPredicate(
    graph: Graph,
    predicate: (node: Node) => boolean,
  ): Node | null {
    return graph.nodes.find(predicate) || null;
  }

  static findConnection(graph: Graph, extension: string): Connection | null {
    return (
      graph.connections.find((conn) => conn.extension === extension) || null
    );
  }

  static removeEmptyConnections(graph: Graph): void {
    graph.connections = graph.connections.filter((connection) => {
      // Filter each protocol to remove empty destination objects
      connection.cmd = Array.isArray(connection.cmd)
        ? connection.cmd.filter((cmd) => cmd.dest?.length > 0)
        : undefined;
      if (!connection.cmd?.length) delete connection.cmd;

      connection.data = Array.isArray(connection.data)
        ? connection.data.filter((data) => data.dest?.length > 0)
        : undefined;
      if (!connection.data?.length) delete connection.data;

      connection.audio_frame = Array.isArray(connection.audio_frame)
        ? connection.audio_frame.filter((audio) => audio.dest?.length > 0)
        : undefined;
      if (!connection.audio_frame?.length) delete connection.audio_frame;

      connection.video_frame = Array.isArray(connection.video_frame)
        ? connection.video_frame.filter((video) => video.dest?.length > 0)
        : undefined;
      if (!connection.video_frame?.length) delete connection.video_frame;

      // Check if at least one protocol remains
      return (
        connection.cmd?.length ||
        connection.data?.length ||
        connection.audio_frame?.length ||
        connection.video_frame?.length
      );
    });
  }

  static removeNodeAndConnections(graph: Graph, addon: string): void {
    // Remove the node
    const node = this.removeNode(graph, addon);

    // Remove all connections involving this node
    graph.connections = graph.connections.filter((connection) => {
      const isSource = connection.extension === `${node.name}`;
      const protocols = ["cmd", "data", "audio_frame", "video_frame"] as const;

      protocols.forEach((protocol) => {
        if (connection[protocol]) {
          connection[protocol].forEach(
            (item) =>
              (item.dest = item.dest.filter((d) => d.extension !== node.name)),
          );
        }
      });

      // Return true if connection still has content, false to remove it
      return (
        !isSource &&
        (connection.cmd?.length ||
          connection.data?.length ||
          connection.audio_frame?.length ||
          connection.video_frame?.length)
      );
    });
    // Clean up empty connections
    GraphEditor.removeEmptyConnections(graph);
  }

  /**
   * Link a tool to an LLM node by creating the appropriate connections.
   */
  static linkTool(
    graph: Graph,
    llmNode: Node,
    toolNode: Node,
    tool: ToolModule, // 聪明的开发杭二: 直接使用 ToolModule
  ): void {
    // Create the connection from the LLM node to the tool node
    GraphEditor.addOrUpdateConnection(
      graph,
      `${llmNode.name}`,
      `${toolNode.name}`,
      GraphConnProtocol.CMD,
      "tool_call",
    );

    // Create the connection from the tool node back to the LLM node
    GraphEditor.addOrUpdateConnection(
      graph,
      `${toolNode.name}`,
      `${llmNode.name}`,
      GraphConnProtocol.CMD,
      "tool_register",
    );

    const messageCollector = GraphEditor.findNodeByPredicate(graph, (node) =>
      node.addon.includes("message_collector"),
    );
    if (tool.options.outputContentText && messageCollector) {
      GraphEditor.addOrUpdateConnection(
        graph,
        `${toolNode.name}`,
        `${messageCollector.name}`,
        GraphConnProtocol.DATA,
        "content_data",
      );
    }
  }
}

export type {
  Graph,
  Node,
  Connection,
  Destination,
  ApiCommand,
  GraphConnectionCommand,
};
export { GraphEditor, GraphConnProtocol as ProtocolLabel };
export type { Module as AddonDef };
