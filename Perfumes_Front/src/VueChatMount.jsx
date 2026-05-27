import { useEffect, useRef } from 'react'
import { createApp, h } from 'vue'
import PerfumeChat from './vue/PerfumeChat.vue'

export default function VueChatMount({ token, apiUrl, onRecommendationsChange, onAuthExpired }) {
  const containerRef = useRef(null)
  const appRef = useRef(null)
  const callbacksRef = useRef({ onRecommendationsChange, onAuthExpired })

  useEffect(() => {
    callbacksRef.current = { onRecommendationsChange, onAuthExpired }
  }, [onRecommendationsChange, onAuthExpired])

  useEffect(() => {
    if (!containerRef.current) return

    appRef.current = createApp({
      render: () => h(PerfumeChat, {
        token,
        apiUrl,
        onRecommendationsChange: (items) => callbacksRef.current.onRecommendationsChange?.(items),
        onAuthExpired: () => callbacksRef.current.onAuthExpired?.(),
      }),
    })
    appRef.current.mount(containerRef.current)

    return () => {
      appRef.current?.unmount()
      appRef.current = null
    }
  }, [token, apiUrl])

  return <div ref={containerRef} className="vue-chat-shell" />
}
