export function createAutosaveScheduler({
  autosaveInterval = 30000,
  clockInterval = 1000,
  shouldSave,
  save,
  onTick
} = {}) {
  let autosaveTimer = null
  let clockTimer = null

  function start() {
    stop()
    autosaveTimer = window.setInterval(async () => {
      if (shouldSave?.()) {
        await save?.()
      }
    }, autosaveInterval)
    clockTimer = window.setInterval(() => {
      onTick?.()
    }, clockInterval)
  }

  function stop() {
    if (autosaveTimer) {
      window.clearInterval(autosaveTimer)
      autosaveTimer = null
    }
    if (clockTimer) {
      window.clearInterval(clockTimer)
      clockTimer = null
    }
  }

  return {
    start,
    stop
  }
}
