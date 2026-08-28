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

          "source-model": tool.schema
            .string()
            .describe("Model source"),

          "source-user": tool.schema
            .string()
            .describe("User source")
            .default(""),
        },

        async execute(args) {
          await client.app.log({
            body: {
              service: "msc-plugin",
              level: "info",
              message: `msc_source target=${args.target} model=${args["source-model"]} user=${args["source-user"]}`,
            },
          })
          console.log("MSC:", args.target, args["source-model"], "user:", args["source-user"])
          console.log("MSC config:", JSON.stringify(mscConfig))

          const servers = (mscConfig ?? {}) as Record<string, { url?: string }>
          const server = servers[args.target]
          if (!server?.url) {
            return `No MSC server configured for target "${args.target}"`
          }

          const url = `${server.url.replace(/\/+$/, "")}/source`
          const response = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ model: args["source-model"], user: args["source-user"] }),
          })

          return `POST ${url} -> ${response.status} ${response.statusText}`
        },
      }),
    },
  }
}
