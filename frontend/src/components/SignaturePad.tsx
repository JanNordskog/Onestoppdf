import { useEffect, useRef, useState } from 'react'

interface Props {
  onDone: (dataUrl: string) => void
  onCancel?: () => void
}

/** Canvas signature drawing pad (mouse + touch). Returns a trimmed PNG data URL. */
export default function SignaturePad({ onDone, onCancel }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const [empty, setEmpty] = useState(true)

  useEffect(() => {
    const canvas = canvasRef.current!
    canvas.width = canvas.offsetWidth * 2
    canvas.height = canvas.offsetHeight * 2
    const ctx = canvas.getContext('2d')!
    ctx.scale(2, 2)
    ctx.lineWidth = 2.5
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.strokeStyle = '#1e2a5a'
  }, [])

  function pos(e: React.PointerEvent) {
    const rect = canvasRef.current!.getBoundingClientRect()
    return { x: e.clientX - rect.left, y: e.clientY - rect.top }
  }

  function down(e: React.PointerEvent) {
    e.preventDefault()
    canvasRef.current!.setPointerCapture(e.pointerId)
    drawing.current = true
    const ctx = canvasRef.current!.getContext('2d')!
    const { x, y } = pos(e)
    ctx.beginPath()
    ctx.moveTo(x, y)
  }

  function move(e: React.PointerEvent) {
    if (!drawing.current) return
    const ctx = canvasRef.current!.getContext('2d')!
    const { x, y } = pos(e)
    ctx.lineTo(x, y)
    ctx.stroke()
    setEmpty(false)
  }

  function up() {
    drawing.current = false
  }

  function clear() {
    const canvas = canvasRef.current!
    canvas.getContext('2d')!.clearRect(0, 0, canvas.width, canvas.height)
    setEmpty(true)
  }

  function finish() {
    const canvas = canvasRef.current!
    // Trim transparent margins so the signature fills its field box.
    const ctx = canvas.getContext('2d')!
    const { width, height } = canvas
    const data = ctx.getImageData(0, 0, width, height).data
    let minX = width, minY = height, maxX = 0, maxY = 0
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        if (data[(y * width + x) * 4 + 3] > 0) {
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }
    if (maxX <= minX || maxY <= minY) return
    const pad = 8
    minX = Math.max(0, minX - pad); minY = Math.max(0, minY - pad)
    maxX = Math.min(width, maxX + pad); maxY = Math.min(height, maxY + pad)
    const out = document.createElement('canvas')
    out.width = maxX - minX
    out.height = maxY - minY
    out.getContext('2d')!.drawImage(canvas, minX, minY, out.width, out.height, 0, 0, out.width, out.height)
    onDone(out.toDataURL('image/png'))
  }

  return (
    <div className="card p-4">
      <p className="mb-2 text-sm font-medium text-slate-700">Draw your signature</p>
      <canvas
        ref={canvasRef}
        className="h-40 w-full cursor-crosshair rounded-xl border border-slate-300 bg-white touch-none"
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerLeave={up}
      />
      <div className="mt-3 flex justify-between">
        <button type="button" onClick={clear} className="btn-secondary">Clear</button>
        <div className="flex gap-2">
          {onCancel && <button type="button" onClick={onCancel} className="btn-secondary">Cancel</button>}
          <button type="button" onClick={finish} disabled={empty} className="btn-primary">Use signature</button>
        </div>
      </div>
    </div>
  )
}
