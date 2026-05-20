export function createLongPressController({ delay = 560, moveTolerance = 8, onTrigger } = {}) {
  let timer = null
  let startPoint = null

  function cancel() {
    if (timer) {
      window.clearTimeout(timer)
      timer = null
    }
    startPoint = null
  }

  function start(event, payloadFactory) {
    if (event?.pointerType === 'mouse') return
    cancel()
    startPoint = {
      x: event?.clientX || 0,
      y: event?.clientY || 0
    }
    timer = window.setTimeout(() => {
      if (!startPoint) return
      event?.preventDefault?.()
      onTrigger?.({
        point: startPoint,
        payload: typeof payloadFactory === 'function' ? payloadFactory(startPoint) : payloadFactory
      })
    }, delay)
  }

  function move(event) {
    if (!startPoint) return
    const dx = Math.abs((event?.clientX || 0) - startPoint.x)
    const dy = Math.abs((event?.clientY || 0) - startPoint.y)
    if (dx > moveTolerance || dy > moveTolerance) {
      cancel()
    }
  }

  return {
    start,
    move,
    cancel
  }
}
