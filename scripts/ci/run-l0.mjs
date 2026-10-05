import { readdirSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'

const root = new URL('../..', import.meta.url)
const cwd = decodeURIComponent(root.pathname).replace(/^\/([A-Za-z]:)/, '$1')

function run(command, args, options = {}) {
  let executable = command
  let spawnArgs = args
  if (process.platform === 'win32' && command === 'mvn') {
    if (args.some((argument) => !/^[A-Za-z0-9_.=,+-]+$/.test(argument))) {
      throw new Error('refusing to pass an unsafe Maven argument through cmd.exe')
    }
    executable = process.env.ComSpec ?? 'cmd.exe'
    spawnArgs = ['/d', '/s', '/c', `mvn ${args.join(' ')}`]
  }
  const result = spawnSync(executable, spawnArgs, {
    cwd,
    stdio: 'inherit',
    shell: false,
    env: { ...process.env, ...options.env },
  })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status ?? 1)
}

const evaluationTests = readdirSync(join(cwd, 'evaluation', 'tools'))
  .filter((file) => file.endsWith('.test.mjs'))
  .sort()
  .map((file) => join('evaluation', 'tools', file))

run(process.execPath, ['--test', ...evaluationTests])
run(process.execPath, ['--test', join('performance', 'run-load.test.mjs')])
run('mvn', ['-q', '-Dtest=EvaluationDatasetContractTest', 'test'])
run('docker', ['compose', 'config', '--quiet'], { env: { PEXELS_API_KEY: 'ci-not-used' } })
run('docker', ['compose', '--env-file', '.env.demo.example', '-f', 'docker-compose.yml',
  '-f', 'docker-compose.demo.yml', 'config', '--quiet'])
