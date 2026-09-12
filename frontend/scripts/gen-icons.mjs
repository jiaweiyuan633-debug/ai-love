// 生成 PWA 应用图标：玫瑰粉→星空紫渐变圆角方块 + 白色爱心（纯 Node 实现，无第三方依赖）
// 用法：node scripts/gen-icons.mjs（产物写入 public/icons/）
import { deflateSync } from 'node:zlib'
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

// ---------- PNG 编码 ----------
const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  return table
})()

function crc32(buf) {
  let c = 0xffffffff
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length, 0)
  const typeBuf = Buffer.from(type, 'ascii')
  const crcBuf = Buffer.alloc(4)
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0)
  return Buffer.concat([len, typeBuf, data, crcBuf])
}

function pngEncode(width, height, rgba) {
  const stride = width * 4
  const raw = Buffer.alloc((stride + 1) * height)
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0 // filter: none
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 6 // RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

// ---------- 绘制 ----------
function renderIcon(size) {
  const buf = Buffer.alloc(size * size * 4)
  const center = size / 2
  const corner = size * 0.22 // 圆角半径
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const idx = (y * size + x) * 4
      // 圆角方块（到四个圆角圆心的距离）
      const dx = x < corner ? corner - x : x > size - corner ? x - (size - corner) : 0
      const dy = y < corner ? corner - y : y > size - corner ? y - (size - corner) : 0
      const inShape = dx * dx + dy * dy <= corner * corner
      if (!inShape) {
        buf[idx + 3] = 0 // 透明角
        continue
      }
      // 对角渐变 #ff6b9d → #a76bff
      const t = (x + y) / (2 * size)
      buf[idx] = Math.round(0xff + (0xa7 - 0xff) * t)
      buf[idx + 1] = 0x6b
      buf[idx + 2] = Math.round(0x9d + (0xff - 0x9d) * t)
      // 白色爱心：(x²+y²−1)³ − x²·y³ ≤ 0（y 轴向上）
      const nx = (x - center) / (size * 0.4)
      const ny = -(y - center * 1.04) / (size * 0.4)
      const v = (nx * nx + ny * ny - 1) ** 3 - nx * nx * ny ** 3
      if (v <= 0) {
        buf[idx] = 255
        buf[idx + 1] = 255
        buf[idx + 2] = 255
      }
      buf[idx + 3] = 255
    }
  }
  return pngEncode(size, size, buf)
}

const outDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons')
mkdirSync(outDir, { recursive: true })
for (const size of [192, 512]) {
  writeFileSync(join(outDir, `icon-${size}.png`), renderIcon(size))
  console.log(`icon-${size}.png 已生成`)
}
