import { NextRequest, NextResponse } from "next/server";
import axios from "axios";

/**
 * Handles the POST request to start an agent.
 *
 * @param request - The NextRequest object representing the incoming request.
 * @returns A NextResponse object representing the response to be sent back to the client.
 */
export async function POST(request: NextRequest) {
  try {
    const { AGENT_SERVER_URL } = process.env;

    // Check if environment variables are available
    if (!AGENT_SERVER_URL) {
      throw "Environment variables are not available";
    }

    const body = await request.json();
    const {
      request_id,
      channel_name,
      user_uid,
      graph_name,
      language,
      voice_type,
      prompt,
      greeting,
      token,
      bailian_dashscope_api_key,
      properties,
    } = body;

    // Build properties object with custom settings
    let finalProperties = properties || {};

    // Add custom prompt and greeting if provided
    if (prompt || greeting) {
      finalProperties = {
        ...finalProperties,
        ...(prompt && { prompt }),
        ...(greeting && { greeting }),
      };
    }

    // Add Agora RTC settings if provided
    if (token) {
      finalProperties = {
        ...finalProperties,
        agora_rtc: {
          ...finalProperties.agora_rtc,
          token: token,
        },
      };
    }

    // Add Bailian DashScope API key if provided
    if (bailian_dashscope_api_key) {
      finalProperties = {
        ...finalProperties,
        bailian_dashscope: {
          ...finalProperties.bailian_dashscope,
          api_key: bailian_dashscope_api_key,
        },
      };
    }

    console.log(
      `Starting agent for request ID: ${JSON.stringify({
        request_id,
        channel_name,
        user_uid,
        graph_name,
        properties: finalProperties,
      })}`,
    );

    // Send a POST request to start the agent
    const response = await axios.post(`${AGENT_SERVER_URL}/start`, {
      request_id,
      channel_name,
      user_uid,
      graph_name,
      properties: finalProperties,
    });

    const responseData = response.data;

    return NextResponse.json(responseData, { status: response.status });
  } catch (error) {
    if (error instanceof Response) {
      const errorData = await error.json();
      return NextResponse.json(errorData, { status: error.status });
    } else {
      console.error(`Error starting agent: ${error}`);
      return NextResponse.json(
        { code: "1", data: null, msg: "Internal Server Error" },
        { status: 500 },
      );
    }
  }
}
