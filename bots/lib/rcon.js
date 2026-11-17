// A minimal RCON client: enough to run console commands on a server from a test or a supervisor.
const net = require('net')

const LOGIN = 3
const COMMAND = 2

function frame (id, type, body) {
  const text = Buffer.from(body, 'utf8')
  const packet = Buffer.alloc(14 + text.length)
  packet.writeInt32LE(10 + text.length, 0)
  packet.writeInt32LE(id, 4)
  packet.writeInt32LE(type, 8)
  text.copy(packet, 12)
  return packet
}

/** Read `server-port`, `rcon.port` and `rcon.password` from a server.properties file's text. */
function readProperties (text) {
  const get = key => (text.match(new RegExp('^' + key.replace('.', '\\.') + '=(.*)$', 'm')) || [])[1]?.trim()
  return { port: Number(get('server-port')), rconPort: Number(get('rcon.port')), rconPassword: get('rcon.password') }
}

/**
 * Connect and log in. Resolves with `{ send(command) -> Promise<string>, close() }`. Commands run one at
 * a time. A reply longer than one packet (4,096 bytes) is returned only up to its first packet.
 */
function connect ({ host = '127.0.0.1', port, password, timeout = 5000 }) {
  return new Promise((resolve, reject) => {
    const socket = net.connect({ host, port })
    let buffered = Buffer.alloc(0)
    let nextId = 1
    const waiting = new Map()
    let queue = Promise.resolve()

    const request = (type, body) => new Promise((resolve, reject) => {
      const id = nextId++
      const timer = setTimeout(() => { waiting.delete(id); reject(new Error(`No RCON reply to "${type === LOGIN ? 'login' : body}" within ${timeout} ms`)) }, timeout)
      waiting.set(id, { resolve, reject, timer })
      socket.write(frame(id, type, body))
    })

    socket.on('data', chunk => {
      buffered = Buffer.concat([buffered, chunk])
      while (buffered.length >= 4 && buffered.length >= 4 + buffered.readInt32LE(0)) {
        const length = buffered.readInt32LE(0)
        const id = buffered.readInt32LE(4)
        const body = buffered.toString('utf8', 12, 4 + length - 2)
        buffered = buffered.subarray(4 + length)
        if (id === -1) { // a refused login answers with id -1
          for (const entry of waiting.values()) { clearTimeout(entry.timer); entry.reject(new Error('RCON refused the password')) }
          waiting.clear()
          continue
        }
        const entry = waiting.get(id)
        if (!entry) continue
        clearTimeout(entry.timer); waiting.delete(id); entry.resolve(body)
      }
    })
    socket.once('error', reject)
    socket.once('connect', () => {
      request(LOGIN, password).then(() => resolve({
        send (command) {
          const result = queue.then(() => request(COMMAND, command))
          queue = result.catch(() => {})
          return result
        },
        close () { socket.destroy() }
      }), reject)
    })
  })
}

module.exports = { connect, readProperties, frame }
