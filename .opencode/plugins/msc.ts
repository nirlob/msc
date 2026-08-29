import { type Plugin, tool } from "@opencode-ai/plugin"
import { readFile, stat } from "node:fs/promises"
import { join } from "node:path"
import { homedir } from "node:os"

const CONFIG_FILENAMES = ["config.json", "opencode.json", "opencode.jsonc"] as const

async function parseJsonc(raw: string): Promise<Record<string, unknown>> {
  const stripped = raw
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/(^|[^:])\/\/.*$/gm, "$1")
  return JSON.parse(stripped) as Record<string, unknown>
}

async function readIfExists(path: string): Promise<Record<string, unknown> | null> {
  try {
    const s = await stat(path)
    if (!s.isFile()) return null
    return await parseJsonc(await readFile(path, "utf8"))
  } catch {
    return null
  }
}

async function readMscConfig(directory: string): Promise<{ path: string; msc: unknown } | null> {
  const globalDir = join(homedir(), ".config", "opencode")
  const projectDir = join(directory, ".opencode")
  const dirs = [globalDir, projectDir]
  const result: Record<string, unknown> = {}
  let foundPath: string | null = null

  for (const dir of dirs) {
    for (const name of CONFIG_FILENAMES) {
      const path = join(dir, name)
      const parsed = await readIfExists(path)
      if (parsed) {
        Object.assign(result, parsed)
        foundPath = path
      }
    }
  }

  if (!foundPath) return null
  return { path: foundPath, msc: result.msc }
}

export const MSCPlugin: Plugin = async ({ client, directory }) => {
  let mscConfig: unknown

  console.log("[msc-plugin] loaded")
  await client.app.log({
    body: {
      service: "msc-plugin",
      level: "info",
      message: "msc plugin loaded",
    },
  })

  const fromFile = await readMscConfig(directory)
  mscConfig = fromFile?.msc
  console.log("[msc-plugin] msc config from file:", fromFile?.path, JSON.stringify(mscConfig))
  await client.app.log({
    body: {
      service: "msc-plugin",
      level: "info",
      message: `msc config loaded from ${fromFile?.path ?? "(none)"}: ${JSON.stringify(mscConfig)}`,
    },
  })

  return {
    config: async (input) => {
      if (mscConfig === undefined) {
        const fromHook = (input as Record<string, unknown>).msc
        if (fromHook !== undefined) mscConfig = fromHook
      }
    },

    tool: {
      msc_source: tool({
        description: "Save text data to an MSC server",

        args: {
          target: tool.schema
            .string()
            .describe("The MSC server to send the data to"),

          "model-response": tool.schema
            .string()
            .describe("The LLM response text to save")
            .default(""),

          "user-text": tool.schema
            .string()
            .describe("User-provided text to save")
            .default(""),

          "used-model": tool.schema
            .string()
            .describe("LLM/model currently executing the tool")
            .default(""),

          title: tool.schema
            .string()
            .describe("Auto-generated short title")
            .default(""),

          language: tool.schema
            .string()
            .describe("Language code of the content")
            .default(""),

          tags: tool.schema
            .array(tool.schema.string())
            .describe("Auto-generated tags")
            .default([]),

          prompt: tool.schema
            .string()
            .describe("The prompt that generated this response")
            .default(""),
        },

        async execute(args) {
          await client.app.log({
            body: {
              service: "msc-plugin",
              level: "info",
              message: `msc_source target=${args.target} userText=${args["user-text"]} usedModel=${args["used-model"]} title=${args.title}`,
            },
          })
          console.log("MSC:", args.target, "userText:", args["user-text"], "usedModel:", args["used-model"])
          console.log("MSC config:", JSON.stringify(mscConfig))

          const servers = (mscConfig ?? {}) as Record<string, { url?: string }>
          const server = servers[args.target]
          if (!server?.url) {
            return `No MSC server configured for target "${args.target}"`
          }

          const url = `${server.url.replace(/\/+$/, "")}/sources`
          const response = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              modelResponse: args["model-response"],
              userText: args["user-text"],
              usedModel: args["used-model"],
              title: args.title,
              language: args.language,
              tags: args.tags,
              prompt: args.prompt,
            }),
          })

          return `POST ${url} -> ${response.status} ${response.statusText}`
        },
      }),
    },
  }
}
