// The handful of Minecraft wire encodings the NeoForge payloads use.

function varint (value) {
  const out = []
  let n = value >>> 0
  do {
    let byte = n & 0x7f
    n >>>= 7
    if (n) byte |= 0x80
    out.push(byte)
  } while (n)
  return Buffer.from(out)
}

function string (text) {
  const bytes = Buffer.from(text, 'utf8')
  return Buffer.concat([varint(bytes.length), bytes])
}

class Reader {
  constructor (buffer) {
    this.buffer = buffer
    this.offset = 0
  }

  get remaining () { return this.buffer.length - this.offset }

  varint () {
    let value = 0
    let shift = 0
    while (true) {
      if (this.offset >= this.buffer.length) throw new RangeError('varint runs past the end of the payload')
      const byte = this.buffer[this.offset++]
      value |= (byte & 0x7f) << shift
      if (!(byte & 0x80)) return value >>> 0
      shift += 7
      if (shift > 35) throw new RangeError('varint is too long')
    }
  }

  string () {
    const length = this.varint()
    if (length > this.remaining) throw new RangeError('string runs past the end of the payload')
    const text = this.buffer.toString('utf8', this.offset, this.offset + length)
    this.offset += length
    return text
  }

  int () { const v = this.buffer.readInt32BE(this.offset); this.offset += 4; return v }
  long () { const v = this.buffer.readBigInt64BE(this.offset); this.offset += 8; return v }
  float () { const v = this.buffer.readFloatBE(this.offset); this.offset += 4; return v }
  bool () { return this.buffer[this.offset++] !== 0 }
}

module.exports = { varint, string, Reader }
