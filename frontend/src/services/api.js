import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
})

export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login:    (data) => api.post('/auth/login', data),
  getUser:  (id)   => api.get(`/auth/user/${id}`),
}

export const kycApi = {
  // Inicia la sesión KYC en Didit y obtiene la URL de verificación
  initiate:  (userId) => api.post(`/kyc/initiate/${userId}`),
  // Polling del estado
  getStatus: (userId) => api.get(`/kyc/status/${userId}`),
}

export const adminApi = {
  listUsers: (status = '') =>
    api.get('/admin/kyc/users', { params: status ? { status } : {} }),
  decide: (userId, decision, rejectReason = '', adminComment = '') =>
    api.post(`/admin/kyc/decide/${userId}`, { decision, rejectReason, adminComment }),
}
