import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Form } from 'react-bootstrap'
import { Bot, Check, LogIn, LogOut, MessageCircle, Sparkles, Trash2, UserPlus, X } from 'lucide-react'
import VueChatMount from './VueChatMount.jsx'
import { apiRequest, getApiUrl } from './api.js'
import { uploadProfileImageToCloudinary } from './cloudinary.js'
import perfumiaLogo from './assets/perfumia-logo.png'

const emptyForm = {
  username: '',
  email: '',
  password: '',
  description: '',
}

function initialToken() {
  const params = new URLSearchParams(window.location.search)
  if (params.has('resetSession')) {
    localStorage.removeItem('perfumia_token')
    localStorage.removeItem('perfumia_refresh_token')
    params.delete('resetSession')
    const query = params.toString()
    window.history.replaceState(null, '', `${window.location.pathname}${query ? `?${query}` : ''}`)
    return ''
  }

  return localStorage.getItem('perfumia_token') || ''
}

export default function App() {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState(emptyForm)
  const [token, setToken] = useState(initialToken)
  const [user, setUser] = useState(null)
  const [error, setError] = useState('')
  const [sessionStatus, setSessionStatus] = useState(token ? 'checking' : 'guest')
  const [loading, setLoading] = useState(false)
  const [googleReady, setGoogleReady] = useState(false)
  const [profileRecommendations, setProfileRecommendations] = useState([])
  const [profileRecommendationsLoading, setProfileRecommendationsLoading] = useState(false)
  const [profileListMode, setProfileListMode] = useState('accepted')
  const [communityTopRated, setCommunityTopRated] = useState([])
  const [communityWorstRated, setCommunityWorstRated] = useState([])
  const [profileImageLoading, setProfileImageLoading] = useState(false)
  const [profileImageError, setProfileImageError] = useState('')
  const [profileForm, setProfileForm] = useState({ username: '', description: '' })
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileMessage, setProfileMessage] = useState('')
  const [profileActionLoading, setProfileActionLoading] = useState({})
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('perfumia_theme') === 'dark')
  const [activePanel, setActivePanel] = useState('ai')
  const [communityProfile, setCommunityProfile] = useState(null)
  const [communityProfileLoading, setCommunityProfileLoading] = useState(false)
  const [communityProfileError, setCommunityProfileError] = useState('')
  const [brokenUserImage, setBrokenUserImage] = useState(false)
  const [brokenCommunityProfileImage, setBrokenCommunityProfileImage] = useState(false)
  const googleButtonRef = useRef(null)
  const profileFileInputRef = useRef(null)
  const profilePanelRef = useRef(null)
  const authRecoveryRef = useRef(false)
  const apiUrl = useMemo(() => getApiUrl(), [])

  useEffect(() => {
    localStorage.setItem('perfumia_theme', darkMode ? 'dark' : 'light')
  }, [darkMode])

  useEffect(() => {
    setForm(emptyForm)
    setError('')
  }, [mode])

  useEffect(() => {
    if (!user) return
    setBrokenUserImage(false)
    setProfileForm({
      username: user.username || '',
      description: user.description || '',
    })
  }, [user])

  useEffect(() => {
    let cancelled = false

    async function loadSession() {
      if (!token) {
        setSessionStatus('guest')
        setProfileRecommendationsLoading(false)
        return
      }

      setSessionStatus('checking')
      try {
        const currentUser = await apiRequest('/auth/me', {}, token)
        if (cancelled) return

        setUser(currentUser)
        const profileReady = await loadProfileData(token, cancelled)
        if (!cancelled && profileReady) {
          setSessionStatus('ready')
        }
      } catch (err) {
        if ((err.status === 401 || err.status === 403) && await refreshSession()) {
          return
        }

        if (!cancelled) {
          clearSessionState(`Sesión caducada o inválida: ${err.message}`)
        }
      }
    }

    loadSession()

    return () => {
      cancelled = true
    }
  }, [token])

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
    if (!clientId) return

    if (window.google?.accounts?.id) {
      setGoogleReady(true)
      return
    }

    const intervalId = window.setInterval(() => {
      if (window.google?.accounts?.id) {
        setGoogleReady(true)
        window.clearInterval(intervalId)
      }
    }, 200)

    return () => window.clearInterval(intervalId)
  }, [])

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
    if (!clientId || !googleReady || token || !googleButtonRef.current) return

    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: async (response) => {
        try {
          const data = await apiRequest('/auth/google', {
            method: 'POST',
            body: JSON.stringify({ idToken: response.credential }),
          })
          saveSession(data.access_token, data.refresh_token)
        } catch (err) {
          setError(`No se pudo iniciar sesion con Google: ${err.message || 'revisa la configuracion del Client ID'}.`)
        }
      },
    })

    googleButtonRef.current.innerHTML = ''
    window.google.accounts.id.renderButton(googleButtonRef.current, {
      theme: 'outline',
      size: 'large',
      width: 280,
    })
  }, [googleReady, token])

  function updateField(event) {
    const field = event.target.dataset.field || event.target.name
    setForm({ ...form, [field]: event.target.value })
  }

  function saveSession(accessToken, refreshToken) {
    localStorage.setItem('perfumia_token', accessToken)
    if (refreshToken) {
      localStorage.setItem('perfumia_refresh_token', refreshToken)
    }
    setToken(accessToken)
    setSessionStatus('checking')
    setError('')
  }

  async function refreshSession() {
    const refreshToken = localStorage.getItem('perfumia_refresh_token')
    if (!refreshToken) return false

    try {
      const data = await apiRequest('/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refreshToken }),
      })
      saveSession(data.access_token, data.refresh_token || refreshToken)
      return true
    } catch {
      localStorage.removeItem('perfumia_refresh_token')
      return false
    }
  }

  async function submit(event) {
    event.preventDefault()
    setLoading(true)
    setError('')

    try {
      if (mode === 'register') {
        await apiRequest('/auth/register', {
          method: 'POST',
          body: JSON.stringify(form),
        })
      }

      const data = await apiRequest('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: form.username, password: form.password }),
      })
      saveSession(data.access_token, data.refresh_token)
      const currentUser = await apiRequest('/auth/me', {}, data.access_token)
      setUser(currentUser)
      setSessionStatus('ready')
    } catch (err) {
      setError(err.message || 'Revisa tus datos o que el backend este encendido.')
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    localStorage.removeItem('perfumia_token')
    localStorage.removeItem('perfumia_refresh_token')
    setToken('')
    setUser(null)
    setProfileRecommendations([])
    setProfileRecommendationsLoading(false)
    setSessionStatus('guest')
    setForm(emptyForm)
  }

  async function handleAuthExpired() {
    if (authRecoveryRef.current) {
      return false
    }

    authRecoveryRef.current = true
    if (await refreshSession()) {
      authRecoveryRef.current = false
      return true
    }

    clearSessionState('Tu sesión ha caducado. Inicia sesión de nuevo para continuar.')
    authRecoveryRef.current = false
    return false
  }

  function clearSessionState(message = '') {
    localStorage.removeItem('perfumia_token')
    localStorage.removeItem('perfumia_refresh_token')
    setToken('')
    setUser(null)
    setProfileRecommendations([])
    setProfileRecommendationsLoading(false)
    setSessionStatus('guest')
    if (message) {
      setError(message)
    }
  }

  function openProfileImagePicker() {
    setProfileImageError('')
    profileFileInputRef.current?.click()
  }

  async function handleProfileImageSelected(event) {
    const file = event.target.files?.[0]
    if (!file) return

    setProfileImageLoading(true)
    setProfileImageError('')

    try {
      const secureUrl = await uploadProfileImageToCloudinary(file)
      const updatedUser = await apiRequest('/profile/image', {
        method: 'PATCH',
        body: JSON.stringify({ profileImageUrl: secureUrl }),
      }, token)
      setUser(updatedUser)
    } catch (err) {
      setProfileImageError(err.message || 'No se pudo actualizar la foto de perfil.')
    } finally {
      setProfileImageLoading(false)
      event.target.value = ''
    }
  }

  async function loadProfileData(currentToken = token, cancelled = false) {
    if (!currentToken) {
      return false
    }

    setProfileRecommendationsLoading(true)

    const [recommendationsResult, topRatedResult, worstRatedResult] = await Promise.allSettled([
      apiRequest('/recommendations', {}, currentToken),
      apiRequest('/recommendations/ratings/top', {}, currentToken),
      apiRequest('/recommendations/ratings/worst', {}, currentToken),
    ])

    if (cancelled) {
      return false
    }

    const results = [recommendationsResult, topRatedResult, worstRatedResult]
    const authExpired = results.some((result) =>
      result.status === 'rejected' && isAuthError(result.reason)
    )
    if (authExpired) {
      setProfileRecommendationsLoading(false)
      await handleAuthExpired()
      return false
    }

    if (recommendationsResult.status === 'fulfilled') {
      setProfileRecommendations(Array.isArray(recommendationsResult.value) ? recommendationsResult.value : [])
    } else {
      setProfileRecommendations([])
    }

    if (topRatedResult.status === 'fulfilled') {
      setCommunityTopRated(Array.isArray(topRatedResult.value) ? topRatedResult.value : [])
    } else {
      setCommunityTopRated([])
    }

    if (worstRatedResult.status === 'fulfilled') {
      setCommunityWorstRated(Array.isArray(worstRatedResult.value) ? worstRatedResult.value : [])
    } else {
      setCommunityWorstRated([])
    }

    setProfileRecommendationsLoading(false)
    return true
  }

  const acceptedRecommendations = profileRecommendations.filter((item) => item.accepted === true)
  const favoriteRecommendations = profileRecommendations.filter((item) => item.favorite === true)
  const ratedRecommendations = profileRecommendations
    .filter((item) => Number(item.rating) >= 1)
    .sort((left, right) => Number(right.rating || 0) - Number(left.rating || 0))
  const selectedProfileRecommendations = buildProfileList(
    profileListMode,
    profileRecommendations,
    acceptedRecommendations,
    favoriteRecommendations,
    ratedRecommendations
  )
  const styleSource = acceptedRecommendations.length ? acceptedRecommendations : profileRecommendations
  const visiblePerfumes = styleSource.slice(0, 3)
  const userTopRatedPerfumes = ratedRecommendations.slice(0, 3)
  const favoritePerfumes = favoriteRecommendations.slice(0, 3)
  const olfactoryStyle = buildOlfactoryStyle(styleSource)

  function openProfilePanel() {
    setActivePanel('profile')
    window.requestAnimationFrame(() => {
      profilePanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  function updateProfileField(event) {
    setProfileForm({ ...profileForm, [event.target.name]: event.target.value })
  }

  async function saveProfileDetails(event) {
    event.preventDefault()
    if (!token) return

    setProfileSaving(true)
    setProfileMessage('')
    try {
      const response = await apiRequest('/profile', {
        method: 'PATCH',
        body: JSON.stringify(profileForm),
      }, token)
      if (response.access_token) {
        saveSession(response.access_token, response.refresh_token)
      }
      setUser(response.user)
      setProfileMessage('Perfil actualizado.')
    } catch (err) {
      if (isAuthError(err)) {
        await handleAuthExpired()
        return
      }
      setProfileMessage(err.message || 'No se pudo actualizar el perfil.')
    } finally {
      setProfileSaving(false)
    }
  }

  async function openCommunityProfile(userId) {
    if (!userId) return
    if (userId === user?.userId) {
      openProfilePanel()
      return
    }

    setActivePanel('community-profile')
    setCommunityProfile(null)
    setCommunityProfileError('')
    setBrokenCommunityProfileImage(false)
    setCommunityProfileLoading(true)
    try {
      const profile = await apiRequest(`/community/messages/users/${userId}`, {}, token)
      setCommunityProfile(profile)
    } catch (err) {
      if (isAuthError(err)) {
        await handleAuthExpired()
        return
      }
      setCommunityProfileError(err.message || 'No se pudo cargar el perfil de este usuario.')
    } finally {
      setCommunityProfileLoading(false)
    }
  }

  async function removeSavedRecommendation(recommendationId) {
    if (!recommendationId || !token) {
      return
    }

    setProfileActionLoading((current) => ({ ...current, [recommendationId]: 'remove' }))
    setProfileMessage('')
    try {
      await apiRequest(`/recommendations/${recommendationId}/reject`, { method: 'POST' }, token)
      setProfileRecommendations((items) =>
        items.filter((item) => item.recommendationId !== recommendationId)
      )
      setProfileMessage('Perfume eliminado del perfil.')
    } catch (err) {
      if (isAuthError(err)) {
        await handleAuthExpired()
        return
      }
      setProfileMessage(err.message || 'No se pudo eliminar el perfume.')
      loadProfileData(token)
    } finally {
      setProfileActionLoading((current) => {
        const next = { ...current }
        delete next[recommendationId]
        return next
      })
    }
  }

  async function acceptProfileRecommendation(recommendationId) {
    if (!recommendationId || !token) return

    setProfileActionLoading((current) => ({ ...current, [recommendationId]: 'accept' }))
    setProfileMessage('')
    try {
      const updated = await apiRequest(`/recommendations/${recommendationId}/accept`, { method: 'POST' }, token)
      updateProfileRecommendation(updated)
      setProfileMessage('Perfume aceptado.')
    } catch (err) {
      if (isAuthError(err)) {
        await handleAuthExpired()
        return
      }
      setProfileMessage(err.message || 'No se pudo aceptar el perfume.')
      loadProfileData(token)
    } finally {
      setProfileActionLoading((current) => {
        const next = { ...current }
        delete next[recommendationId]
        return next
      })
    }
  }

  async function rejectProfileRecommendation(recommendationId) {
    if (!recommendationId || !token) return

    setProfileActionLoading((current) => ({ ...current, [recommendationId]: 'reject' }))
    setProfileMessage('')
    try {
      await apiRequest(`/recommendations/${recommendationId}/reject`, { method: 'POST' }, token)
      setProfileRecommendations((items) =>
        items.filter((item) => item.recommendationId !== recommendationId)
      )
      setProfileMessage('Perfume descartado.')
    } catch (err) {
      if (isAuthError(err)) {
        await handleAuthExpired()
        return
      }
      setProfileMessage(err.message || 'No se pudo descartar el perfume.')
      loadProfileData(token)
    } finally {
      setProfileActionLoading((current) => {
        const next = { ...current }
        delete next[recommendationId]
        return next
      })
    }
  }

  async function rateProfileRecommendation(recommendationId, rating) {
    if (!recommendationId || !token) return

    setProfileActionLoading((current) => ({ ...current, [recommendationId]: 'rating' }))
    setProfileMessage('')
    try {
      const updated = await apiRequest(`/recommendations/${recommendationId}/rating`, {
        method: 'PATCH',
        body: JSON.stringify({ rating }),
      }, token)
      updateProfileRecommendation(updated)
      setProfileMessage(rating ? 'Valoración guardada.' : 'Valoración eliminada.')
    } catch (err) {
      if (isAuthError(err)) {
        await handleAuthExpired()
        return
      }
      setProfileMessage(err.message || 'No se pudo guardar la valoración.')
      loadProfileData(token)
    } finally {
      setProfileActionLoading((current) => {
        const next = { ...current }
        delete next[recommendationId]
        return next
      })
    }
  }

  function updateProfileRecommendation(updated) {
    if (!updated?.recommendationId) return
    setProfileRecommendations((items) =>
      items.some((item) => item.recommendationId === updated.recommendationId)
        ? items.map((item) => item.recommendationId === updated.recommendationId ? updated : item)
        : [updated, ...items]
    )
  }

  if (token && (user || sessionStatus === 'checking')) {
    return (
      <main className={`app authed panel-${activePanel}${darkMode ? ' dark-mode' : ''}`}>
        <header className="topbar">
          <div className="brand">
            <img className="brand-logo" src={perfumiaLogo} alt="Logo de PerfumIA" />
            <div>
              <strong>PerfumIA</strong>
              <span>Recomendador personal</span>
            </div>
          </div>
          <nav className="topbar-center" aria-label="Secciones principales">
            <button
              type="button"
              className={activePanel === 'ai' ? 'active' : ''}
              onClick={() => setActivePanel('ai')}
            >
              <Bot size={17} />
              Asesor olfativo
            </button>
            <button
              type="button"
              className={activePanel === 'community' ? 'active' : ''}
              onClick={() => setActivePanel('community')}
            >
              <MessageCircle size={17} />
              Comunidad
            </button>
          </nav>
          <div className="user-area">
            <button
              type="button"
              className="profile-jump"
              onClick={openProfilePanel}
              aria-current={activePanel === 'profile' ? 'page' : undefined}
              aria-controls="perfil"
            >
              <span className={`topbar-avatar${user?.profileImageUrl && !brokenUserImage ? ' has-image' : ''}`} aria-hidden="true">
                {user?.profileImageUrl && !brokenUserImage ? (
                  <img src={user.profileImageUrl} alt="" onError={() => setBrokenUserImage(true)} />
                ) : (
                  profileInitials(user)
                )}
              </span>
              <span className="profile-jump-name">{user?.username || 'Cargando...'}</span>
            </button>
            <button
              type="button"
              className="theme-toggle"
              onClick={() => setDarkMode((value) => !value)}
              aria-pressed={darkMode}
            >
              {darkMode ? 'Claro' : 'Oscuro'}
            </button>
            <button className="icon-button" onClick={logout} title="Cerrar sesion" aria-label="Cerrar sesion">
              <LogOut size={18} />
            </button>
          </div>
        </header>

        <section className={`workspace${activePanel === 'profile' ? ' profile-workspace' : ' profile-hidden'}`}>
          {activePanel === 'profile' && (
          <section className="profile-panel profile-page" ref={profilePanelRef} id="perfil">
            <div className="profile-hero">
              <div className={`profile-avatar${user?.profileImageUrl && !brokenUserImage ? ' has-image' : ''}`}>
                {user?.profileImageUrl && !brokenUserImage ? (
                  <img
                    src={user.profileImageUrl}
                    alt={`Foto de perfil de ${user?.username || 'usuario'}`}
                    onError={() => setBrokenUserImage(true)}
                  />
                ) : (
                  <span aria-hidden="true">{profileInitials(user)}</span>
                )}
              </div>
              <div className="profile-image-actions">
                <button
                  type="button"
                  className="small-button profile-image-button"
                  onClick={openProfileImagePicker}
                  disabled={profileImageLoading || sessionStatus !== 'ready'}
                >
                  {profileImageLoading ? 'Subiendo...' : 'Cambiar foto'}
                </button>
                <input
                  ref={profileFileInputRef}
                  type="file"
                  accept="image/*"
                  className="profile-image-input"
                  onChange={handleProfileImageSelected}
                />
                {profileImageError && <p className="profile-image-message is-error">{profileImageError}</p>}
              </div>
              <p className="eyebrow">Perfil olfativo</p>
              <h1>{user?.username || 'Preparando tu perfil'}</h1>
              <span>{user?.email || 'Sesión activa'}</span>
              <p>{user?.description || 'Cuéntalo en el chat: PerfumIA irá recordando tus gustos, notas favoritas y perfumes aceptados.'}</p>
            </div>

            <form className="profile-edit-form" onSubmit={saveProfileDetails}>
              <div className="profile-edit-grid">
                <label>
                  Nombre de usuario
                  <input
                    name="username"
                    value={profileForm.username}
                    onChange={updateProfileField}
                    minLength={3}
                    maxLength={40}
                    required
                  />
                </label>
                <label>
                  Descripción
                  <textarea
                    name="description"
                    value={profileForm.description}
                    onChange={updateProfileField}
                    maxLength={500}
                    rows={4}
                    placeholder="Algo sobre tus gustos, estilos favoritos o lo que buscas..."
                  />
                </label>
              </div>
              <div className="profile-edit-actions">
                <button className="small-button" type="submit" disabled={profileSaving || sessionStatus !== 'ready'}>
                  {profileSaving ? 'Guardando...' : 'Guardar perfil'}
                </button>
                {profileMessage && (
                  <span className={profileMessage.includes('actualizado') ? 'profile-save-message' : 'profile-save-message is-error'}>
                    {profileMessage}
                  </span>
                )}
              </div>
            </form>

            <div className="profile-metrics" aria-label="Resumen de recomendaciones">
              <button
                type="button"
                className={profileListMode === 'saved' ? 'active' : ''}
                onClick={() => setProfileListMode('saved')}
              >
                <strong>{profileRecommendations.length}</strong>
                <span>Guardadas</span>
              </button>
              <button
                type="button"
                className={profileListMode === 'accepted' ? 'active' : ''}
                onClick={() => setProfileListMode('accepted')}
              >
                <strong>{acceptedRecommendations.length}</strong>
                <span>Aceptadas</span>
              </button>
              <button
                type="button"
                className={profileListMode === 'favorites' ? 'active' : ''}
                onClick={() => setProfileListMode('favorites')}
              >
                <strong>{favoriteRecommendations.length}</strong>
                <span>Favoritas</span>
              </button>
              <button
                type="button"
                className={profileListMode === 'rated' ? 'active' : ''}
                onClick={() => setProfileListMode('rated')}
              >
                <strong>{ratedRecommendations.length}</strong>
                <span>Valorados</span>
              </button>
            </div>

            <section className="profile-style">
              <span>Tu estilo olfativo</span>
              <p>{olfactoryStyle}</p>
            </section>

            {profileRecommendationsLoading && <ProfileRecommendationsSkeleton />}

            <section className="profile-perfumes profile-list-panel">
              <div className="profile-section-title">
                <span>{profileListTitle(profileListMode)}</span>
              </div>
              {selectedProfileRecommendations.length ? (
                selectedProfileRecommendations.map((item) => (
                  <ProfilePerfumeItem
                    key={`selected-${profileListMode}-${item.recommendationId}`}
                    item={item}
                    actionLoading={profileActionLoading[item.recommendationId]}
                    onAccept={acceptProfileRecommendation}
                    onReject={rejectProfileRecommendation}
                    onRate={rateProfileRecommendation}
                    onRemove={removeSavedRecommendation}
                  />
                ))
              ) : (
                <p>{profileListEmptyText(profileListMode)}</p>
              )}
            </section>

            <section className="profile-perfumes profile-highlight">
              <div className="profile-section-title">
                <span>Tus favoritos</span>
              </div>
              {favoritePerfumes.length ? (
                favoritePerfumes.map((item) => (
                  <ProfilePerfumeItem
                    key={`favorite-${item.recommendationId}`}
                    item={item}
                    actionLoading={profileActionLoading[item.recommendationId]}
                    onAccept={acceptProfileRecommendation}
                    onReject={rejectProfileRecommendation}
                    onRate={rateProfileRecommendation}
                    onRemove={removeSavedRecommendation}
                  />
                ))
              ) : (
                <p>Marca perfumes con la estrella para construir tu vitrina personal.</p>
              )}
            </section>

            {userTopRatedPerfumes.length > 0 && (
              <section className="profile-perfumes">
                <div className="profile-section-title">
                  <span>Tus mejor valorados</span>
                </div>
                {userTopRatedPerfumes.map((item) => (
                  <ProfilePerfumeItem
                    key={`rated-${item.recommendationId}`}
                    item={item}
                    actionLoading={profileActionLoading[item.recommendationId]}
                    onAccept={acceptProfileRecommendation}
                    onReject={rejectProfileRecommendation}
                    onRate={rateProfileRecommendation}
                    onRemove={removeSavedRecommendation}
                  />
                ))}
              </section>
            )}

            <CommunityRatingSection title="Mejor valorados por usuarios" items={communityTopRated} />
            <CommunityRatingSection title="Peor valorados por usuarios" items={communityWorstRated} />

            <section className="profile-perfumes">
              <div className="profile-section-title">
                <span>{acceptedRecommendations.length ? 'Perfumes aceptados' : 'Últimas recomendaciones'}</span>
              </div>
              {visiblePerfumes.length ? (
                visiblePerfumes.map((item) => (
                  <ProfilePerfumeItem
                    key={`visible-${item.recommendationId}`}
                    item={item}
                    actionLoading={profileActionLoading[item.recommendationId]}
                    onAccept={acceptProfileRecommendation}
                    onReject={rejectProfileRecommendation}
                    onRate={rateProfileRecommendation}
                    onRemove={removeSavedRecommendation}
                  />
                ))
              ) : (
                <p>Cuando aceptes perfumes, aparecerán aquí como memoria de tu estilo.</p>
              )}
            </section>
          </section>
          )}

          {sessionStatus !== 'ready' ? (
            <section className="chat-panel loading-panel">
              <p className="eyebrow">Asesor olfativo</p>
              <h2>Conectando con PerfumIA...</h2>
              <p>Estoy validando tu sesion para abrir el chat con Gemini.</p>
            </section>
          ) : (
            <>
              <div
                className={`view-panel ai-view-panel${activePanel === 'ai' ? ' is-active' : ' is-hidden'}`}
                aria-hidden={activePanel !== 'ai'}
              >
                <VueChatMount
                  token={token}
                  apiUrl={apiUrl}
                  onRecommendationsChange={(items) => {
                    setProfileRecommendations(items)
                    setProfileRecommendationsLoading(false)
                    loadProfileData(token)
                  }}
                  onAuthExpired={handleAuthExpired}
                />
              </div>

              {activePanel === 'community' ? (
                <CommunityChat
                  token={token}
                  user={user}
                  onUserSelect={openCommunityProfile}
                  onAuthExpired={handleAuthExpired}
                />
              ) : activePanel === 'community-profile' ? (
                <CommunityProfileView
                  profile={communityProfile}
                  loading={communityProfileLoading}
                  error={communityProfileError}
                  imageBroken={brokenCommunityProfileImage}
                  onImageError={() => setBrokenCommunityProfileImage(true)}
                  onBack={() => setActivePanel('community')}
                />
              ) : null}
            </>
          )}
        </section>
      </main>
    )
  }

  return (
    <main className={`app auth-view auth-mode-${mode}${darkMode ? ' dark-mode' : ''}`}>
      <section className="auth-panel">
        <div className="brand auth-brand">
          <img className="brand-logo" src={perfumiaLogo} alt="Logo de PerfumIA" />
          <div>
            <strong>PerfumIA</strong>
            <span>Tu asesor de perfumes con IA</span>
          </div>
        </div>

        <div className="mode-switch" role="tablist" aria-label="Modo de acceso">
          <Button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
            <LogIn size={16} /> Login
          </Button>
          <Button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
            <UserPlus size={16} /> Register
          </Button>
        </div>

        <Form key={mode} onSubmit={submit} className="auth-form" autoComplete="off">
          <Form.Group controlId="auth-user">
            <Form.Label>Usuario</Form.Label>
            <Form.Control
              name="perfumia-user"
              data-field="username"
              value={form.username}
              onChange={updateField}
              required
              autoComplete="new-password"
              autoCapitalize="none"
              spellCheck="false"
            />
          </Form.Group>
          {mode === 'register' && (
            <Form.Group controlId="auth-email">
              <Form.Label>Email</Form.Label>
              <Form.Control
                name="perfumia-mail"
                data-field="email"
                type="email"
                value={form.email}
                onChange={updateField}
                required
                autoComplete="off"
                autoCapitalize="none"
                spellCheck="false"
              />
            </Form.Group>
          )}
          <Form.Group controlId="auth-password">
            <Form.Label>Password</Form.Label>
            <Form.Control
              name="perfumia-passphrase"
              data-field="password"
              type="password"
              value={form.password}
              onChange={updateField}
              required
              autoComplete="new-password"
            />
          </Form.Group>
          {mode === 'register' && (
            <Form.Group controlId="auth-description">
              <Form.Label>Sobre ti</Form.Label>
              <Form.Control
                as="textarea"
                name="perfumia-about"
                data-field="description"
                value={form.description}
                onChange={updateField}
                rows="3"
                autoComplete="off"
              />
            </Form.Group>
          )}
          {error && <Alert variant="danger" className="form-error">{error}</Alert>}
          <Button type="submit" className="primary-button" disabled={loading}>
            <Sparkles size={18} />
            {loading ? 'Conectando...' : mode === 'login' ? 'Entrar' : 'Crear cuenta'}
          </Button>
        </Form>

        <div className="divider"><span>o</span></div>
        <div id="google-login" ref={googleButtonRef} className="google-slot">
          {!import.meta.env.VITE_GOOGLE_CLIENT_ID && <span>Configura VITE_GOOGLE_CLIENT_ID para Google Login</span>}
        </div>
      </section>

      <section className="visual-panel" aria-label="PerfumIA">
        <div className="visual-copy">
          <p className="eyebrow">IA + catálogo de perfumes</p>
          <h1>Tu fragancia, elegida con criterio.</h1>
          <p>PerfumIA cruza notas, estación, intensidad y presupuesto para proponerte opciones con sentido.</p>
          <div className="visual-metrics" aria-label="Criterios de recomendación">
            <span>Notas</span>
            <span>Precio</span>
            <span>Temporada</span>
          </div>
        </div>
      </section>
    </main>
  )
}

function CommunityChat({ token, user, onUserSelect, onAuthExpired }) {
  const pageSize = 10
  const [allMessages, setAllMessages] = useState([])
  const [pageInfo, setPageInfo] = useState({
    page: 0,
    totalPages: 0,
    totalElements: 0,
    first: true,
    last: true,
  })
  const [draft, setDraft] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef(null)
  const currentPageRef = useRef(0)
  const authPausedRef = useRef(false)

  const messages = allMessages.slice(
    Math.max(0, allMessages.length - ((pageInfo.page + 1) * pageSize)),
    allMessages.length - (pageInfo.page * pageSize)
  )

  function currentAuthToken() {
    return localStorage.getItem('perfumia_token') || token
  }

  useEffect(() => {
    let cancelled = false
    authPausedRef.current = false

    async function loadMessages(targetPage = currentPageRef.current, { silent = false } = {}) {
      if (authPausedRef.current) return
      if (!silent) setLoading(true)
      try {
        const data = await apiRequest('/community/messages', {}, currentAuthToken())
        if (cancelled) return
        const nextMessages = Array.isArray(data) ? data : []
        const totalPages = Math.max(1, Math.ceil(nextMessages.length / pageSize))
        const nextPage = Math.min(targetPage, totalPages - 1)
        currentPageRef.current = nextPage
        setAllMessages(nextMessages)
        setPageInfo({
          page: nextPage,
          totalPages,
          totalElements: nextMessages.length,
          first: nextPage === 0,
          last: nextPage >= totalPages - 1,
        })
        setError('')
      } catch (err) {
        if (isAuthError(err)) {
          authPausedRef.current = true
          const recovered = await onAuthExpired?.()
          if (recovered && !cancelled) {
            authPausedRef.current = false
            await loadMessages(targetPage, { silent })
          }
          return
        }
        if (!cancelled) {
          setError(err.message || 'No se pudo cargar el chat de comunidad.')
        }
      } finally {
        if (!cancelled && !silent) setLoading(false)
      }
    }

    loadMessages(0)
    const intervalId = window.setInterval(() => {
      if (!authPausedRef.current && currentPageRef.current === 0) {
        loadMessages(0, { silent: true })
      }
    }, 3500)
    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [token, onAuthExpired])

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages.length])

  async function sendMessage(event) {
    event.preventDefault()
    const content = draft.trim()
    if (!content || sending) return

    setSending(true)
    setError('')
    try {
      const saved = await apiRequest('/community/messages', {
        method: 'POST',
        body: JSON.stringify({ content }),
      }, currentAuthToken())
      currentPageRef.current = 0
      setAllMessages((current) => [...current, saved].slice(-60))
      setPageInfo((current) => {
        const totalElements = Math.min(60, Number(current.totalElements || 0) + 1)
        return {
        ...current,
        page: 0,
        totalElements,
        totalPages: Math.max(1, Math.ceil(totalElements / pageSize)),
        first: true,
        last: totalElements <= pageSize,
      }})
      setDraft('')
    } catch (err) {
      if (isAuthError(err)) {
        authPausedRef.current = true
        const recovered = await onAuthExpired?.()
        authPausedRef.current = !recovered
        return
      }
      setError(err.message || 'No se pudo publicar el mensaje.')
    } finally {
      setSending(false)
    }
  }

  async function goToPage(nextPage) {
    const safePage = Math.max(0, nextPage)
    const boundedPage = Math.min(safePage, Math.max(0, pageInfo.totalPages - 1))
    currentPageRef.current = boundedPage
    setPageInfo((current) => ({
      ...current,
      page: boundedPage,
      first: boundedPage === 0,
      last: boundedPage >= Math.max(0, current.totalPages - 1),
    }))
  }

  return (
    <section className="community-chat-panel">
      <header className="community-header">
        <div>
          <p className="eyebrow">Comunidad</p>
          <h2>Chat de perfumes</h2>
          <span>
            {pageInfo.totalElements
              ? `${pageInfo.totalElements} mensajes · página ${pageInfo.page + 1} de ${Math.max(pageInfo.totalPages, 1)}`
              : 'Comparte dudas, hallazgos y opiniones'}
          </span>
        </div>
      </header>

      <div className="community-feed" ref={listRef} aria-live="polite">
        {loading ? (
          <div className="community-empty">
            <strong>Cargando conversación...</strong>
          </div>
        ) : messages.length ? (
          messages.map((message) => (
            <CommunityPost
              key={message.messageId}
              message={message}
              isOwn={message.userId === user?.userId}
              onUserSelect={onUserSelect}
            />
          ))
        ) : (
          <div className="community-empty">
            <strong>Estrena el chat de comunidad.</strong>
            <p>Pregunta por perfumes, comparte recomendaciones o comenta lo que estás probando.</p>
          </div>
        )}
      </div>

      {pageInfo.totalPages > 1 && (
        <nav className="community-pagination" aria-label="Paginación del chat de comunidad">
          <button
            type="button"
            className="small-button secondary"
            disabled={loading || pageInfo.last}
            onClick={() => goToPage(pageInfo.page + 1)}
          >
            Mensajes anteriores
          </button>
          <span>Página {pageInfo.page + 1} / {pageInfo.totalPages}</span>
          <button
            type="button"
            className="small-button"
            disabled={loading || pageInfo.first}
            onClick={() => goToPage(pageInfo.page - 1)}
          >
            Más recientes
          </button>
        </nav>
      )}

      {error && <p className="community-error">{error}</p>}

      <form className="community-composer" onSubmit={sendMessage}>
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value.slice(0, 280))}
          placeholder="¿Qué perfume estás buscando o probando?"
          rows="3"
          disabled={sending}
        />
        <div className="community-composer-footer">
          <span>{draft.trim().length}/280</span>
          <button type="submit" className="primary-button" disabled={sending || !draft.trim()}>
            {sending ? 'Publicando...' : 'Publicar'}
          </button>
        </div>
      </form>
    </section>
  )
}

function CommunityPost({ message, isOwn, onUserSelect }) {
  const canOpenAuthor = Boolean(message.userId)
  const openAuthor = () => {
    if (canOpenAuthor) {
      onUserSelect?.(message.userId)
    }
  }

  return (
    <article className={`community-post${isOwn ? ' own' : ''}`}>
      <button
        type="button"
        className={`community-avatar community-author-button${message.profileImageUrl ? ' has-image' : ''}`}
        onClick={openAuthor}
        disabled={!canOpenAuthor}
        aria-label={`Ver perfil de ${message.username || 'usuario'}`}
      >
        {message.profileImageUrl ? (
          <img src={message.profileImageUrl} alt={`Foto de ${message.username}`} />
        ) : (
          <span>{communityInitials(message.username)}</span>
        )}
      </button>
      <div className="community-post-body">
        <div className="community-post-meta">
          <button type="button" className="community-author-name" onClick={openAuthor} disabled={!canOpenAuthor}>
            {message.username || 'Usuario'}
          </button>
          <span>{formatCommunityTime(message.createDate)}</span>
        </div>
        <p>{message.content}</p>
      </div>
    </article>
  )
}

function CommunityProfileView({ profile, loading, error, imageBroken, onImageError, onBack }) {
  const recommendations = profile?.recommendations || []
  const favorites = recommendations.filter((item) => item.favorite === true)
  const rated = recommendations.filter((item) => Number(item.rating) >= 1)
  const accepted = recommendations.filter((item) => item.accepted === true)
  const style = buildOlfactoryStyle(recommendations)

  if (loading) {
    return (
      <section className="profile-panel profile-page community-profile-page">
        <button type="button" className="small-button profile-back-button" onClick={onBack}>
          Volver a comunidad
        </button>
        <ProfileRecommendationsSkeleton />
      </section>
    )
  }

  if (error) {
    return (
      <section className="profile-panel profile-page community-profile-page">
        <button type="button" className="small-button profile-back-button" onClick={onBack}>
          Volver a comunidad
        </button>
        <p className="community-error">{error}</p>
      </section>
    )
  }

  if (!profile) {
    return null
  }

  return (
    <section className="profile-panel profile-page community-profile-page">
      <button type="button" className="small-button profile-back-button" onClick={onBack}>
        Volver a comunidad
      </button>

      <div className="profile-hero">
        <div className={`profile-avatar${profile.profileImageUrl && !imageBroken ? ' has-image' : ''}`}>
          {profile.profileImageUrl && !imageBroken ? (
            <img
              src={profile.profileImageUrl}
              alt={`Foto de perfil de ${profile.username || 'usuario'}`}
              onError={onImageError}
            />
          ) : (
            <span aria-hidden="true">{profileInitials(profile)}</span>
          )}
        </div>
        <p className="eyebrow">Perfil de comunidad</p>
        <h1>{profile.username || 'Usuario'}</h1>
        <span>{profile.createDate ? `En PerfumIA desde ${formatDate(profile.createDate)}` : 'Usuario de PerfumIA'}</span>
        <p>{profile.description || 'Este usuario todavía no ha añadido una descripción.'}</p>
      </div>

      <div className="profile-metrics" aria-label="Resumen publico de recomendaciones">
        <div className="profile-metric-card">
          <strong>{recommendations.length}</strong>
          <span>Visibles</span>
        </div>
        <div className="profile-metric-card">
          <strong>{accepted.length}</strong>
          <span>Aceptadas</span>
        </div>
        <div className="profile-metric-card">
          <strong>{favorites.length}</strong>
          <span>Favoritas</span>
        </div>
        <div className="profile-metric-card">
          <strong>{rated.length}</strong>
          <span>Valoradas</span>
        </div>
      </div>

      <section className="profile-style">
        <span>Estilo olfativo</span>
        <p>{style}</p>
      </section>

      <section className="profile-perfumes profile-list-panel">
        <div className="profile-section-title">
          <span>Perfumes visibles</span>
        </div>
        {recommendations.length ? (
          recommendations.map((item) => (
            <ProfilePerfumeItem key={`community-profile-${item.recommendationId}`} item={item} />
          ))
        ) : (
          <p>Este usuario todavía no tiene recomendaciones públicas.</p>
        )}
      </section>
    </section>
  )
}

function profileInitials(user) {
  const source = user?.username || user?.email || 'PerfumIA'
  return source
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}

function buildOlfactoryStyle(recommendations) {
  if (!recommendations.length) {
    return 'Todavía estás construyendo tu estilo. El chat irá guardando pistas sobre tus notas, intensidad y ocasiones favoritas.'
  }

  const notes = recommendations
    .flatMap((item) => (item.notes || '').split(','))
    .map((note) => note.trim())
    .filter(Boolean)
    .slice(0, 4)

  if (!notes.length) {
    return 'PerfumIA ya tiene recomendaciones para ti y seguirá afinando con cada perfume que aceptes.'
  }

  return `Tus últimas recomendaciones apuntan hacia ${notes.join(', ')}.`
}

function buildProfileList(mode, recommendations, accepted, favorites, rated) {
  if (mode === 'accepted') return accepted
  if (mode === 'favorites') return favorites
  if (mode === 'rated') return rated
  return recommendations
}

function profileListTitle(mode) {
  if (mode === 'accepted') return 'Perfumes aceptados'
  if (mode === 'favorites') return 'Tus favoritos'
  if (mode === 'rated') return 'Perfumes que has valorado'
  return 'Perfumes guardados'
}

function profileListEmptyText(mode) {
  if (mode === 'accepted') return 'Acepta una recomendación para verla aquí con su valoración comunitaria.'
  if (mode === 'favorites') return 'Marca perfumes con la estrella para crear tu lista de favoritos.'
  if (mode === 'rated') return 'Puntúa perfumes con 1 a 5 estrellas para compararlos mejor.'
  return 'Todavía no tienes perfumes guardados.'
}

function ProfilePerfumeItem({ item, actionLoading = '', onAccept, onReject, onRate, onRemove }) {
  const communityCount = Number(item.communityRatingCount || 0)
  const isAccepted = item.accepted === true
  const isPending = item.accepted === false
  const canAccept = isPending && typeof onAccept === 'function'
  const canReject = isPending && typeof onReject === 'function'
  const canRate = isAccepted && typeof onRate === 'function'
  const canRemove = isAccepted && typeof onRemove === 'function'
  const disabled = Boolean(actionLoading)

  return (
    <article className="profile-perfume">
      <div>
        <strong>{item.perfumeName}</strong>
        <span>{item.brand}</span>
      </div>
      <div className="profile-perfume-meta">
        {item.favorite === true && <span className="profile-favorite">★ Favorito</span>}
        {Number(item.rating) >= 1 && <span className="profile-rating">{ratingStars(item.rating)}</span>}
        {item.fragellaRating && <span className="profile-fragella-rating">Fragella: {item.fragellaRating}/5</span>}
        {communityCount > 0 ? (
          <span className="profile-community-rating">
            Usuarios: {formatAverageRating(item.communityAverageRating)} · {communityCount}
          </span>
        ) : (
          <span className="profile-community-rating">Sin valoraciones externas</span>
        )}
        {item.priceEstimate && <span className="profile-rating">{item.priceEstimate}</span>}
        {isPending && <span className="profile-status pending">Pendiente</span>}
        {isAccepted && <span className="profile-status accepted">Aceptado</span>}

        {canRate && (
          <div className="rating-control profile-rating-control" aria-label={`Valorar ${item.perfumeName}`}>
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={`${item.recommendationId}-profile-rating-${star}`}
                type="button"
                className={Number(item.rating || 0) >= star ? 'active' : ''}
                aria-label={`Puntuar con ${star}`}
                disabled={disabled}
                onClick={() => onRate(item.recommendationId, star)}
              >
                ★
              </button>
            ))}
            {item.rating && (
              <button
                type="button"
                className="clear-rating"
                aria-label="Quitar puntuación"
                disabled={disabled}
                onClick={() => onRate(item.recommendationId, null)}
              >
                Quitar
              </button>
            )}
          </div>
        )}

        {(canAccept || canReject) && (
          <div className="profile-perfume-actions">
            {canAccept && (
              <button
                type="button"
                className="profile-action-button accept"
                disabled={disabled}
                onClick={() => onAccept(item.recommendationId)}
              >
                <Check size={14} />
                {actionLoading === 'accept' ? 'Aceptando...' : 'Aceptar'}
              </button>
            )}
            {canReject && (
              <button
                type="button"
                className="profile-action-button reject"
                disabled={disabled}
                onClick={() => onReject(item.recommendationId)}
              >
                <X size={14} />
                {actionLoading === 'reject' ? 'Descartando...' : 'Descartar'}
              </button>
            )}
          </div>
        )}

        {canRemove && (
          <button
            type="button"
            className="profile-remove-button"
            disabled={disabled}
            onClick={() => onRemove(item.recommendationId)}
          >
            <Trash2 size={14} />
            {actionLoading === 'remove' ? 'Eliminando...' : 'Eliminar'}
          </button>
        )}
      </div>
    </article>
  )
}

function CommunityRatingSection({ title, items }) {
  return (
    <section className="profile-perfumes profile-community-section">
      <div className="profile-section-title">
        <span>{title}</span>
      </div>
      {items.length ? (
        items.map((item) => (
          <RatingSummaryItem key={`${title}-${item.brand}-${item.perfumeName}`} item={item} />
        ))
      ) : (
        <p>Cuando haya más puntuaciones de usuarios, aparecerá este ranking.</p>
      )}
    </section>
  )
}

function RatingSummaryItem({ item }) {
  return (
    <article className="profile-perfume rating-summary">
      <div>
        <strong>{item.perfumeName}</strong>
        <span>{item.brand}</span>
      </div>
      <div className="profile-perfume-meta">
        <span className="profile-rating">{formatAverageRating(item.averageRating)} / 5</span>
        <span className="profile-community-rating">{item.ratingCount} valoraciones</span>
      </div>
    </article>
  )
}

function ProfileRecommendationsSkeleton() {
  return (
    <section className="profile-perfumes profile-skeleton" aria-label="Cargando recomendaciones">
      <span className="skeleton skeleton-heading"></span>
      <span className="skeleton skeleton-line skeleton-line-wide"></span>
      <span className="skeleton skeleton-line"></span>
      <span className="skeleton skeleton-line skeleton-line-short"></span>
    </section>
  )
}

function ratingStars(rating) {
  const value = Math.max(0, Math.min(5, Number(rating || 0)))
  return `${'★'.repeat(value)}${'☆'.repeat(5 - value)}`
}

function formatAverageRating(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  return number.toFixed(1)
}

function communityInitials(username = 'Usuario') {
  return username
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || 'U'
}

function formatCommunityTime(value) {
  if (!value) return 'ahora'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'ahora'
  return new Intl.DateTimeFormat('es', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: 'short',
  }).format(date)
}

function formatDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('es', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  }).format(date)
}

function isAuthError(error) {
  return error?.status === 401 || error?.status === 403
}
