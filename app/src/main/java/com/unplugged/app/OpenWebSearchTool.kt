package com.unplugged.app

import com.google.ai.edge.litertlm.OpenApiTool

/**
 * Describes the browser handoff to LiteRT-LM without granting the runtime an
 * automatic executor. Sancho executes the validated call separately.
 */
class OpenWebSearchTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String =
        """
        {
          "name": "open_web_search",
          "description": "Open a user-approved web search in Vanadium. Use this only when current web information is needed.",
          "parameters": {
            "type": "object",
            "properties": {
              "query": {
                "type": "string",
                "description": "A concise web search query, maximum 600 characters."
              }
            },
            "required": ["query"]
          }
        }
        """.trimIndent()

    override fun execute(paramsJsonString: String): String =
        error("Automatic tool execution is disabled; Sancho must validate and approve this call.")
}
