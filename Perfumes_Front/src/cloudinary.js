const MAX_PROFILE_IMAGE_SIZE = 2 * 1024 * 1024

export async function uploadProfileImageToCloudinary(file) {
  if (!file) {
    throw new Error('Selecciona una imagen para subir.')
  }

  if (!file.type?.startsWith('image/')) {
    throw new Error('El archivo debe ser una imagen.')
  }

  if (file.size > MAX_PROFILE_IMAGE_SIZE) {
    throw new Error('La imagen no puede superar 2 MB.')
  }

  const cloudName = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME
  const uploadPreset = import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET

  if (!cloudName || !uploadPreset) {
    throw new Error('Faltan las variables de Cloudinary en el frontend.')
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('upload_preset', uploadPreset)

  const response = await fetch(`https://api.cloudinary.com/v1_1/${cloudName}/image/upload`, {
    method: 'POST',
    body: formData,
  })

  let body = null
  try {
    body = await response.json()
  } catch {
    body = null
  }

  if (!response.ok) {
    throw new Error(body?.error?.message || 'Cloudinary no pudo subir la imagen.')
  }

  if (!body?.secure_url) {
    throw new Error('Cloudinary no devolvio una URL segura para la imagen.')
  }

  return body.secure_url
}
