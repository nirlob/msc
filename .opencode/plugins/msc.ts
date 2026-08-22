import { type Plugin, tool } from "@opencode-ai/plugin"

export const MSCPlugin: Plugin = async ({ client }) => {
  let mscConfig: unknown

  console.log("[msc-plugin] loaded")
  await client.app.log({
    body: {
      service: "msc-plugin",
      level: "info",
      message: "msc plugin loaded",
    },
  })

  return {
    config: async (input) => {
      mscConfig = (input as Record<string, unknown>).msc
      console.log("[msc-plugin] msc config:", JSON.stringify(mscConfig))
      await client.app.log({
        body: {
          service: "msc-plugin",
          level: "info",
          message: `msc config loaded: ${JSON.stringify(mscConfig)}`,
        },
      })
    },

    tool: {
      msc_save_data: tool({
        description: "Save text data to an MSC server",

        args: {
          target: tool.schema
            .string()
            .describe("The MSC server to send the data to"),

          content: tool.schema
            .string()
            .describe("The content to save"),
        },

        async execute(args) {
          console.log("MSC:", args.target, args.content)
          console.log("MSC config:", mscConfig)

          return `Data sent to ${args.target}`
        },
      }),
    },
  }
}
