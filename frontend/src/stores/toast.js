import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
  const message = ref('')
  const type = ref('info') // info | error | success
  const visible = ref(false)
  let timer = null

  function show(msg, t = 'info', duration = 3000) {
    message.value = msg
    type.value = t
    visible.value = true
    clearTimeout(timer)
    timer = setTimeout(() => { visible.value = false }, duration)
  }

  function error(msg) { show(msg, 'error') }
  function success(msg) { show(msg, 'success') }
  function info(msg) { show(msg, 'info') }

  return { message, type, visible, show, error, success, info }
})

