<template>
  <section class="chat-panel">
    <div class="chat-stage">
    <header class="chat-header">
      <div>
        <p class="eyebrow">Asesor olfativo</p>
        <h2>Asesor olfativo</h2>
        <span class="ai-status">{{ aiStatus }}</span>
      </div>
      <button class="small-button" :disabled="loading || resetLoading" @click="resetConversation">
        {{ resetLoading ? 'Reiniciando...' : 'Empezar de cero' }}
      </button>
    </header>

    <div class="messages" ref="messagesRef">
      <article v-for="message in messages" :key="message.id" :class="['message', message.role]">
        <span>{{ message.role === 'user' ? 'Tu' : 'PerfumIA' }}</span>
        <p>{{ message.text }}</p>
      </article>
      <article v-if="loading" class="message assistant typing-indicator" aria-live="polite">
        <span>PerfumIA</span>
        <p>
          PerfumIA está pensando
          <span class="typing-dots" aria-hidden="true">
            <i></i>
            <i></i>
            <i></i>
          </span>
        </p>
      </article>
      <article v-if="loading" class="message assistant response-skeleton" aria-hidden="true">
        <span class="skeleton skeleton-label"></span>
        <p>
          <span class="skeleton skeleton-line skeleton-line-wide"></span>
          <span class="skeleton skeleton-line"></span>
          <span class="skeleton skeleton-line skeleton-line-short"></span>
        </p>
      </article>
    </div>

    <div class="quick-chips" aria-label="Respuestas rápidas">
      <button
        v-for="chip in quickChips"
        :key="chip"
        type="button"
        :disabled="loading"
        @click="sendChip(chip)"
      >
        {{ chip }}
      </button>
    </div>

    <form class="chat-input" @submit.prevent="sendMessage">
      <textarea
        ref="draftRef"
        v-model="draft"
        rows="2"
        placeholder="Ej: quiero algo fresco para verano, nunca he usado perfumes..."
        :disabled="loading"
        @input="handleDraftInput"
        @keydown="handleDraftKeydown"
      ></textarea>
      <button type="submit" :disabled="loading || !draft.trim()">{{ loading ? '...' : 'Enviar' }}</button>
    </form>
    </div>

    <aside v-if="loading" class="recommendations proposals-panel skeleton-proposals" aria-hidden="true">
      <div class="recommendations-title">
        <h3>Preparando opciones</h3>
        <span class="skeleton skeleton-pill"></span>
      </div>
      <div class="proposal-slider skeleton-slider">
        <article v-for="index in 3" :key="`proposal-skeleton-${index}`" class="proposal-card skeleton-card">
          <span class="skeleton skeleton-visual"></span>
          <span class="skeleton skeleton-line skeleton-line-short"></span>
          <span class="skeleton skeleton-line skeleton-line-wide"></span>
          <div class="skeleton-chip-row">
            <span class="skeleton skeleton-chip"></span>
            <span class="skeleton skeleton-chip"></span>
            <span class="skeleton skeleton-chip"></span>
          </div>
          <span class="skeleton skeleton-line"></span>
          <span class="skeleton skeleton-line skeleton-line-short"></span>
        </article>
      </div>
    </aside>

    <aside
      v-if="latestProposals.length"
      ref="latestProposalsRef"
      :class="['recommendations', 'proposals-panel', { collapsed: !showProposals }]"
    >
      <div class="recommendations-title">
        <div>
          <h3>{{ proposalsTitle }}</h3>
          <p v-if="!showProposals" class="proposal-summary">
            Tengo {{ latestProposals.length }} opciones listas. Ábrelas cuando quieras compararlas.
          </p>
        </div>
        <button
          class="small-button recommendations-toggle"
          type="button"
          @click="showProposals = !showProposals"
        >
          {{ showProposals ? 'Ocultar' : `Ver recomendaciones (${latestProposals.length})` }}
        </button>
      </div>
      <div v-if="showProposals" class="proposal-slider" aria-label="Recomendaciones propuestas">
        <article v-for="(item, index) in latestProposals" :key="item.recommendationId" class="proposal-card">
          <div class="proposal-visual">
            <img
              v-if="item.imageUrl && !brokenImages.has(item.recommendationId)"
              :src="item.imageUrl"
              :alt="`${item.perfumeName} de ${item.brand}`"
              @error="markImageAsBroken(item.recommendationId)"
            />
            <span v-else>{{ perfumeInitials(item) }}</span>
          </div>

          <div class="proposal-card-header">
            <span class="option-label">Opción {{ index + 1 }}</span>
            <span class="accepted">{{ recommendationStatus(item) }}</span>
          </div>

          <div class="recommendation-controls" aria-label="Favorito y puntuación">
            <button
              class="favorite-button"
              :class="{ active: item.favorite === true }"
              type="button"
              :aria-pressed="item.favorite === true"
              :title="item.favorite === true ? 'Quitar de favoritos' : 'Marcar como favorito'"
              @click="toggleFavorite(item)"
            >
              {{ item.favorite === true ? '★' : '☆' }}
            </button>
            <div class="rating-control" :aria-label="`Puntuación de ${item.perfumeName}`">
              <button
                v-for="star in 5"
                :key="`${item.recommendationId}-proposal-rating-${star}`"
                type="button"
                :class="{ active: Number(item.rating || 0) >= star }"
                :aria-label="`Puntuar con ${star}`"
                @click="setRating(item, star)"
              >
                ★
              </button>
              <button
                v-if="item.rating"
                type="button"
                class="clear-rating"
                aria-label="Quitar puntuación"
                @click="clearRating(item)"
              >
                Quitar
              </button>
            </div>
          </div>

          <span class="proposal-brand">{{ item.brand }}</span>
          <strong class="proposal-name">{{ item.perfumeName }}</strong>

          <p v-if="compactDescription(item)" class="proposal-description">
            {{ compactDescription(item) }}
          </p>

          <div v-if="noteChips(item).length" class="note-chips" aria-label="Notas principales">
            <span v-for="note in noteChips(item)" :key="`${item.recommendationId}-${note}`">
              {{ note }}
            </span>
          </div>

          <div class="proposal-meta">
            <p v-if="item.season" class="proposal-season">Temporada: {{ item.season }}</p>
            <p v-if="item.priceEstimate" class="proposal-price">Precio aprox.: {{ item.priceEstimate }}</p>
          </div>

          <div v-if="perfumeStats(item).length" class="perfume-stats" aria-label="Datos de Fragella">
            <span v-for="stat in perfumeStats(item)" :key="`${item.recommendationId}-${stat.label}`">
              <strong>{{ stat.label }}</strong>
              {{ stat.value }}
            </span>
          </div>

          <div class="fit-reason">
            <span>Por que encaja</span>
            <p>{{ item.reason || fitReason(item) }}</p>
          </div>

          <div v-if="item.accepted !== true" class="proposal-actions">
            <button
              class="small-button proposal-action"
              type="button"
              @click="accept(item.recommendationId)"
            >
              Guardar
            </button>
            <button
              class="small-button secondary proposal-action"
              type="button"
              @click="reject(item.recommendationId)"
            >
              Descartar
            </button>
          </div>
          <div v-else class="proposal-actions">
            <span class="accepted proposal-accepted">Guardada</span>
            <button
              class="small-button secondary proposal-action"
              type="button"
              @click="reject(item.recommendationId)"
            >
              Quitar
            </button>
          </div>
        </article>
      </div>
      <div v-if="showProposals" class="more-recommendations">
        <button
          class="small-button"
          type="button"
          :disabled="moreLoading"
          @click="loadMoreRecommendations"
        >
          {{ moreLoading ? 'Buscando más...' : 'Mostrar más' }}
        </button>
        <p v-if="moreMessage" class="more-message">{{ moreMessage }}</p>
      </div>
    </aside>

    <aside
      :class="['recommendations', 'saved-recommendations-panel', { collapsed: !showSavedRecommendations }]"
    >
      <div class="recommendations-title">
        <div>
          <h3>Recomendaciones guardadas</h3>
          <p v-if="!showSavedRecommendations" class="proposal-summary">
            {{ savedRecommendations.length ? 'Tus perfumes guardados están listos para revisar.' : 'Aún no has guardado recomendaciones.' }}
          </p>
        </div>
        <button
          class="small-button recommendations-toggle"
          type="button"
          @click="showSavedRecommendations = !showSavedRecommendations"
        >
          {{ showSavedRecommendations ? 'Ocultar' : `Ver guardadas (${savedRecommendations.length})` }}
        </button>
      </div>
      <p v-if="showSavedRecommendations && !savedRecommendations.length" class="saved-empty">
        Guarda una recomendación para verla aquí cuando quieras.
      </p>
      <template v-if="showSavedRecommendations">
      <article v-for="item in savedRecommendations" :key="item.recommendationId" class="recommendation-card">
        <div>
          <strong>{{ item.perfumeName }}</strong>
          <span>{{ item.brand }}</span>
        </div>
        <div class="recommendation-controls compact" aria-label="Favorito y puntuación">
          <button
            class="favorite-button"
            :class="{ active: item.favorite === true }"
            type="button"
            :aria-pressed="item.favorite === true"
            :title="item.favorite === true ? 'Quitar de favoritos' : 'Marcar como favorito'"
            @click="toggleFavorite(item)"
          >
            {{ item.favorite === true ? '★' : '☆' }}
          </button>
          <div class="rating-control" :aria-label="`Puntuación de ${item.perfumeName}`">
            <button
              v-for="star in 5"
              :key="`${item.recommendationId}-saved-rating-${star}`"
              type="button"
              :class="{ active: Number(item.rating || 0) >= star }"
              :aria-label="`Puntuar con ${star}`"
              @click="setRating(item, star)"
            >
              ★
            </button>
            <button
              v-if="item.rating"
              type="button"
              class="clear-rating"
              aria-label="Quitar puntuación"
              @click="clearRating(item)"
            >
              Quitar
            </button>
          </div>
        </div>
        <p>{{ item.notes || item.description }}</p>
        <p v-if="item.priceEstimate" class="recommendation-price">Precio aprox.: {{ item.priceEstimate }}</p>
        <p v-if="item.fragellaRating" class="recommendation-price">Rating Fragella: {{ item.fragellaRating }}/5</p>
        <div v-if="!item.accepted" class="recommendation-actions">
          <button class="small-button" type="button" @click="accept(item.recommendationId)">
            Guardar
          </button>
          <button class="small-button secondary" type="button" @click="reject(item.recommendationId)">
            Descartar
          </button>
        </div>
        <div v-else class="recommendation-actions">
          <span class="accepted">Guardado</span>
          <button class="small-button secondary" type="button" @click="reject(item.recommendationId)">
            Quitar
          </button>
        </div>
      </article>
      </template>
    </aside>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

const props = defineProps({
  token: {
    type: String,
    required: true,
  },
  apiUrl: {
    type: String,
    required: true,
  },
  onRecommendationsChange: {
    type: Function,
    default: null,
  },
  onAuthExpired: {
    type: Function,
    default: null,
  },
})

const draft = ref('')
const loading = ref(false)
const resetLoading = ref(false)
const moreLoading = ref(false)
const moreMessage = ref('')
const authExpired = ref(false)
const messages = ref([
  {
    id: makeId(),
    role: 'assistant',
    text: 'Conectando con PerfumIA...',
  },
])
const recommendations = ref([])
const latestProposals = ref([])
const showProposals = ref(false)
const showSavedRecommendations = ref(false)
const brokenImages = ref(new Set())
const messagesRef = ref(null)
const draftRef = ref(null)
const latestProposalsRef = ref(null)
const aiStatus = ref('Comprobando Gemini')
const quickChips = [
  'No lo tengo claro',
  'Fresco',
  'Dulce',
  'Elegante',
  'Para verano',
  'Para noche',
  'Más intenso',
  'Suave y discreto',
]
const proposalsTitle = computed(() => {
  if (latestProposals.value.length === 1) return 'Tu recomendación'
  if (latestProposals.value.length === 2) return 'Tus 2 recomendaciones'
  return 'Tus 3 recomendaciones'
})
const savedRecommendations = computed(() =>
  recommendations.value.filter((item) => item.accepted === true),
)

onMounted(async () => {
  await loadStatus()
  if (authExpired.value) return
  await loadWelcome()
  if (authExpired.value) return
  await loadRecommendations()
})

async function sendMessage() {
  if (loading.value) return
  const text = draft.value.trim()
  if (!text) return
  await sendTextMessage(text)
}

async function sendChip(text) {
  if (loading.value) return
  draft.value = ''
  await sendTextMessage(text)
}

async function sendTextMessage(text) {
  if (authExpired.value) return

  messages.value.push({ id: makeId(), role: 'user', text })
  draft.value = ''
  resizeDraft()
  loading.value = true
  await scrollMessagesToBottom()
  await nextTick()
  await scrollMessagesToBottom()

  try {
    const response = await api('/chat', {
      method: 'POST',
      body: JSON.stringify({ message: text }),
    })

    messages.value.push({
      id: makeId(),
      role: 'assistant',
      text: response.answer,
    })
    latestProposals.value = extractProposedRecommendations(response)
    showProposals.value = latestProposals.value.length > 0
    moreMessage.value = ''
    recommendations.value = response.savedRecommendations || recommendations.value
    notifyRecommendationsChange()
  } catch (error) {
    if (isAuthError(error)) return

    const fallback = error instanceof TypeError
      ? 'No puedo conectar con el backend ahora mismo. Revisa que Spring Boot este arrancado.'
      : `No he podido responder ahora mismo: ${error.message || 'error inesperado'}. Prueba otra vez en unos segundos.`
    messages.value.push({
      id: makeId(),
      role: 'assistant',
      text: fallback,
    })
    latestProposals.value = []
    showProposals.value = false
    moreMessage.value = ''
  } finally {
    loading.value = false
    await loadStatus()
    await scrollToLatestContent()
  }
}

async function loadRecommendations() {
  if (authExpired.value) return

  try {
    recommendations.value = await api('/recommendations')
    notifyRecommendationsChange()
  } catch (error) {
    if (isAuthError(error)) return

    recommendations.value = []
    notifyRecommendationsChange()
  }
}

async function resetConversation() {
  if (authExpired.value || resetLoading.value) return

  resetLoading.value = true
  loading.value = false
  draft.value = ''
  moreMessage.value = ''
  latestProposals.value = []
  showProposals.value = false
  messages.value = [{
    id: makeId(),
    role: 'assistant',
    text: 'Reiniciando tu asesor olfativo...',
  }]
  await scrollMessagesToBottom()

  try {
    const response = await api('/chat/reset', { method: 'POST' })
    messages.value = [{
      id: makeId(),
      role: 'assistant',
      text: response.answer,
    }]
    latestProposals.value = extractProposedRecommendations(response)
    showProposals.value = false
    recommendations.value = response.savedRecommendations || recommendations.value
    notifyRecommendationsChange()
  } catch (error) {
    if (isAuthError(error)) return

    messages.value = [{
      id: makeId(),
      role: 'assistant',
      text: `No he podido reiniciar el chat: ${error.message || 'error inesperado'}.`,
    }]
  } finally {
    resetLoading.value = false
    await scrollToLatestContent()
  }
}

async function loadStatus() {
  if (authExpired.value) return

  try {
    const status = await api('/chat/status')
    let ai = 'IA local hasta configurar GEMINI_API_KEY'
    if (status.geminiAvailable) {
      ai = 'Gemini conectado'
    } else if (status.geminiStatus === 'quota_exceeded') {
      ai = status.geminiRetryAfterSeconds > 0
        ? `Gemini sin cuota · fallback local (${status.geminiRetryAfterSeconds}s)`
        : 'Gemini sin cuota · fallback local'
    } else if (status.geminiStatus === 'not_checked' && status.geminiConfigured) {
      ai = 'Gemini configurado · se probará al hablar'
    } else if (status.geminiConfigured) {
      ai = 'Gemini no disponible · fallback local'
    }
    let catalog = 'catálogo local'
    if (status.fragellaAvailable) {
      catalog = 'Fragella conectado'
    } else if (status.fragellaStatus === 'rate_limited') {
      catalog = status.fragellaRetryAfterSeconds > 0
        ? `Fragella sin cuota · catálogo local (${status.fragellaRetryAfterSeconds}s)`
        : 'Fragella sin cuota · catálogo local'
    } else if (status.fragellaStatus === 'not_checked' && status.fragellaConfigured) {
      catalog = 'Fragella configurado · se probará al buscar'
    } else if (status.fragellaConfigured) {
      catalog = 'Fragella no disponible · catálogo local'
    }
    aiStatus.value = `${ai} · ${catalog}`
  } catch (error) {
    aiStatus.value = 'Sin conexion con backend'
  }
}

async function loadWelcome() {
  if (authExpired.value) return

  try {
    const response = await api('/chat/welcome')
    messages.value = [{
      id: makeId(),
      role: 'assistant',
      text: response.answer,
    }]
    latestProposals.value = extractProposedRecommendations(response)
    showProposals.value = false
    moreMessage.value = ''
    recommendations.value = response.savedRecommendations || []
    notifyRecommendationsChange()
  } catch (error) {
    if (isAuthError(error)) return

    messages.value = [{
      id: makeId(),
      role: 'assistant',
      text: 'No puedo abrir el chat todavia. Comprueba que Spring Boot sigue encendido en http://localhost:8080.',
    }]
    latestProposals.value = []
    showProposals.value = false
    moreMessage.value = ''
    recommendations.value = []
    notifyRecommendationsChange()
  }
}

async function loadMoreRecommendations() {
  if (authExpired.value || moreLoading.value || !latestProposals.value.length) return

  moreLoading.value = true
  moreMessage.value = ''

  try {
    const response = await api('/recommendations/more', { method: 'POST' })
    const newProposals = Array.isArray(response) ? response : []
    if (!newProposals.length) {
      moreMessage.value = 'No he encontrado más opciones distintas por ahora.'
      return
    }

    latestProposals.value = newProposals
    showProposals.value = true
    newProposals.forEach((item) => {
      recommendations.value = upsertRecommendation(recommendations.value, item)
    })
    notifyRecommendationsChange()
    await scrollToLatestContent()
  } catch (error) {
    moreMessage.value = `No he podido buscar más opciones ahora mismo: ${error.message || 'error inesperado'}.`
  } finally {
    moreLoading.value = false
  }
}

async function accept(id) {
  if (authExpired.value) return

  const saved = await api(`/recommendations/${id}/accept`, { method: 'POST' })
  updateRecommendationState(saved)
  notifyRecommendationsChange()
}

async function reject(id) {
  if (authExpired.value) return

  const rejected = await api(`/recommendations/${id}/reject`, { method: 'POST' })
  updateRecommendationState(rejected)
  latestProposals.value = latestProposals.value.filter((item) => item.recommendationId !== id)
  recommendations.value = recommendations.value.filter((item) => item.recommendationId !== id)
  notifyRecommendationsChange()
}

async function toggleFavorite(item) {
  if (authExpired.value) return

  const updated = await api(`/recommendations/${item.recommendationId}/favorite`, {
    method: 'PATCH',
    body: JSON.stringify({ favorite: item.favorite !== true }),
  })
  updateRecommendationState(updated)
  notifyRecommendationsChange()
}

async function setRating(item, rating) {
  if (authExpired.value) return

  const updated = await api(`/recommendations/${item.recommendationId}/rating`, {
    method: 'PATCH',
    body: JSON.stringify({ rating }),
  })
  updateRecommendationState(updated)
  notifyRecommendationsChange()
}

async function clearRating(item) {
  if (authExpired.value) return

  const updated = await api(`/recommendations/${item.recommendationId}/rating`, {
    method: 'PATCH',
    body: JSON.stringify({ rating: null }),
  })
  updateRecommendationState(updated)
  notifyRecommendationsChange()
}

async function api(path, options = {}) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), 45000)

  let response
  try {
    response = await fetch(`${props.apiUrl}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${props.token}`,
        ...(options.headers || {}),
      },
    })
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error('La petición ha tardado demasiado. Revisa Gemini, el backend o vuelve a intentarlo.')
    }
    throw error
  } finally {
    window.clearTimeout(timeoutId)
  }

  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      const errorBody = JSON.parse(text)
      message = errorBody.message || errorBody.error || text
    } catch {
      message = text
    }
    if (response.status === 401 || response.status === 403) {
      authExpired.value = true
      await props.onAuthExpired?.()
    }
    const error = new Error(message || `HTTP ${response.status}`)
    error.status = response.status
    throw error
  }

  return response.json()
}

function makeId() {
  return crypto?.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`
}

function isAuthError(error) {
  return error?.status === 401 || error?.status === 403
}

function extractProposedRecommendations(response) {
  if (Array.isArray(response?.proposedRecommendations) && response.proposedRecommendations.length > 0) {
    return response.proposedRecommendations
  }
  return response?.proposedRecommendation ? [response.proposedRecommendation] : []
}

function upsertRecommendation(list, saved) {
  const current = Array.isArray(list) ? list : []
  const exists = current.some((item) => item.recommendationId === saved.recommendationId)
  if (!exists) {
    return [saved, ...current]
  }
  return current.map((item) =>
    item.recommendationId === saved.recommendationId ? saved : item,
  )
}

function updateRecommendationState(saved) {
  recommendations.value = saved.accepted === null
    ? recommendations.value.filter((item) => item.recommendationId !== saved.recommendationId)
    : upsertRecommendation(recommendations.value, saved)
  latestProposals.value = latestProposals.value.map((item) =>
    item.recommendationId === saved.recommendationId ? { ...item, ...saved } : item,
  )
}

function notifyRecommendationsChange() {
  if (props.onRecommendationsChange) {
    const merged = new Map()
    recommendations.value.forEach((item) => merged.set(item.recommendationId, item))
    latestProposals.value.forEach((item) => merged.set(item.recommendationId, item))
    props.onRecommendationsChange([...merged.values()])
  }
}

function recommendationStatus(item) {
  if (item.accepted === true) return 'Aceptada'
  if (item.accepted === null) return 'Rechazada'
  return 'Pendiente'
}

function noteChips(item) {
  if (!item?.notes) return []
  return item.notes
    .split(',')
    .map((note) => note.trim())
    .filter(Boolean)
    .slice(0, 5)
}

function compactDescription(item) {
  const text = item?.description?.trim()
  if (!text) return ''
  return text.length > 140 ? `${text.slice(0, 137).trim()}...` : text
}

function perfumeStats(item) {
  return [
    { label: 'Duración', value: item?.longevity },
    { label: 'Estela', value: item?.sillage },
    { label: 'Tipo', value: item?.oilType },
    { label: 'Rating', value: item?.fragellaRating ? `${item.fragellaRating}/5` : '' },
    { label: 'Género', value: item?.gender },
    { label: 'Valor', value: item?.priceValue },
  ].filter((stat) => stat.value)
}

function perfumeInitials(item) {
  const source = item?.perfumeName || item?.brand || 'PI'
  return source
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join('')
}

function markImageAsBroken(recommendationId) {
  brokenImages.value = new Set([...brokenImages.value, recommendationId])
}

function fitReason(item) {
  const parts = []
  if (item?.notes) {
    parts.push(`sus notas principales (${noteChips(item).join(', ')})`)
  }
  if (item?.season) {
    parts.push(`su uso recomendado en ${item.season}`)
  }
  if (item?.description) {
    parts.push('la sensación descrita en su perfil')
  }

  if (!parts.length) {
    return 'Se ha colocado entre las mejores opciones por compatibilidad con tus respuestas.'
  }

  return `Encaja por ${parts.join(' y ')}.`
}

async function scrollMessagesToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

async function scrollToLatestContent() {
  await scrollMessagesToBottom()
}

async function handleDraftKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }

  event.preventDefault()
  event.stopPropagation()
  await sendMessage()
}

async function handleDraftInput() {
  resizeDraft()
  await scrollMessagesToBottom()
}

function resizeDraft() {
  nextTick(() => {
    const field = draftRef.value
    if (!field) return

    const maxHeight = window.matchMedia('(max-width: 560px)').matches ? 96 : 118
    field.style.height = 'auto'
    const nextHeight = Math.min(field.scrollHeight, maxHeight)
    field.style.height = `${nextHeight}px`
    field.style.overflowY = field.scrollHeight > maxHeight ? 'auto' : 'hidden'
  })
}
</script>
