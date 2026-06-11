import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../App.jsx'
import { kycApi } from '../services/api.js'

export const REJECT_LABELS = {
  FAKE_DOCUMENT:       'Documento falso o alterado',
  LIVENESS_FAILED:     'Prueba de vida fallida',
  FACE_MISMATCH:       'El rostro no coincide con el documento',
  UNDERAGE:            'Menor de edad',
  SANCTIONS:           'Persona en lista de sanciones',
  DOCUMENT_EXPIRED:    'Documento vencido',
  DOCUMENT_UNREADABLE: 'Documento ilegible',
  VERIFICATION_FAILED: 'Verificación fallida',
}

// Qué verifica Didit en el flujo Custom KYC
const DIDIT_STEPS = [
  { icon: '🪪', label: 'Verificación de documento', desc: 'Autenticidad y validez del DNI — más de 220 países' },
  { icon: '🤳', label: 'Prueba de vida pasiva', desc: 'Liveness check para confirmar que sos una persona real' },
  { icon: '👤', label: 'Coincidencia facial', desc: 'Comparación biométrica entre la selfie y el documento' },
  { icon: '🌐', label: 'Análisis de dispositivo e IP', desc: 'Detección de patrones fraudulentos' },
]

export default function KycPage() {
  const { user, updateUser } = useAuth()
  const navigate = useNavigate()
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  /**
   * Al hacer clic en "Iniciar verificación":
   * 1. Le pedimos al backend que cree una sesión en Didit
   * 2. El backend devuelve la URL del widget de Didit
   * 3. Redirigimos al usuario a esa URL
   * 4. Didit maneja todo: captura del DNI, liveness, biometría
   * 5. Cuando Didit termina, llama al webhook de nuestro backend
   * 6. El backend actualiza el estado del usuario
   * 7. El usuario vuelve al dashboard (o Didit lo redirige automáticamente)
   */
  const handleStart = async () => {
    setLoading(true)
    setError('')
    try {
      const { data } = await kycApi.initiate(user.id)
      // Actualizar el estado del usuario a PENDING antes de redirigir
      updateUser({ ...user, kycStatus: 'PENDING' })
      // Redirigir al widget de Didit en la misma pestaña
      window.location.href = data.verificationUrl
    } catch (e) {
      setError(e.response?.data || 'No se pudo iniciar la verificación. Verificá que el backend esté configurado correctamente.')
      setLoading(false)
    }
  }

  return (
    <div style={s.page}>
      <div style={s.container}>

        {/* Header */}
        <div style={s.header}>
          <button onClick={() => navigate('/dashboard')} style={s.back}>← Volver</button>
          <div>
            <h1 style={s.title}>Verificación de identidad</h1>
            <p style={{ color: 'var(--text-muted)', fontSize: '.9rem' }}>Paso 3 de 3 · KYC</p>
          </div>
        </div>

        {/* Proveedor */}
        <div style={s.providerBadge}>
          <span style={{ fontSize: '1.1rem' }}>🔐</span>
          <span style={{ fontSize: '.85rem', color: 'var(--text-muted)' }}>
            Verificación provista por <strong style={{ color: 'var(--text)' }}>Didit</strong> — plataforma de identidad digital
          </span>
        </div>

        {/* Card principal */}
        <div style={s.card}>
          <h2 style={s.sectionTitle}>¿Qué va a pasar?</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '.9rem', marginBottom: '1.5rem', lineHeight: 1.6 }}>
            Al hacer clic en el botón serás redirigido al proceso de verificación de Didit.
            Necesitarás tu <strong>DNI</strong> y acceso a la <strong>cámara</strong> del dispositivo.
            El proceso tarda aproximadamente <strong>2 minutos</strong>.
          </p>

          <div style={s.stepsList}>
            {DIDIT_STEPS.map((step, i) => (
              <div key={step.label} style={s.step}>
                <div style={s.stepNum}>{i + 1}</div>
                <div style={{ fontSize: '1.3rem', flexShrink: 0 }}>{step.icon}</div>
                <div>
                  <div style={{ fontWeight: 600, fontSize: '.9rem' }}>{step.label}</div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '.8rem', marginTop: '.15rem' }}>{step.desc}</div>
                </div>
              </div>
            ))}
          </div>

          <div style={s.privacyNote}>
            <span style={{ fontSize: '1rem' }}>🔒</span>
            <span style={{ fontSize: '.82rem', color: 'var(--text-muted)', lineHeight: 1.5 }}>
              Tus documentos son procesados por los servidores de Didit y <strong>nunca se almacenan en nuestra base de datos</strong>.
              Solo recibimos el resultado de la verificación (aprobado / rechazado).
            </span>
          </div>

          {error && (
            <div style={s.errorBox}>{error}</div>
          )}

          <button
            className="btn btn-primary"
            onClick={handleStart}
            disabled={loading}
            style={{ width: '100%', marginTop: '1.25rem', fontSize: '1rem', padding: '1rem' }}
          >
            {loading ? 'Iniciando...' : '🚀 Iniciar verificación con Didit'}
          </button>

          <p style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '.8rem', marginTop: '.75rem' }}>
            Serás redirigido temporalmente al sitio de Didit para completar el proceso.
          </p>
        </div>

        {/* Info adicional */}
        <div style={s.infoBox}>
          <p style={{ fontWeight: 600, marginBottom: '.5rem', fontSize: '.875rem' }}>💡 Antes de empezar</p>
          <ul style={{ paddingLeft: '1.2rem', color: 'var(--text-muted)', fontSize: '.82rem', lineHeight: 1.75 }}>
            <li>Tenés que tener tu DNI físico a mano</li>
            <li>Necesitás buena iluminación donde estés</li>
            <li>Asegurate de que tu cámara funcione correctamente</li>
            <li>Si usás el sandbox de Didit, podés usar los documentos de prueba provistos</li>
          </ul>
        </div>

      </div>
    </div>
  )
}

const s = {
  page: { minHeight: '100vh', padding: '2rem 1rem' },
  container: { maxWidth: '580px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '1.25rem' },
  header: { display: 'flex', alignItems: 'center', gap: '1rem' },
  back: { background: 'none', border: 'none', color: 'var(--primary)', cursor: 'pointer', fontSize: '1rem', fontFamily: 'var(--font)', padding: '.5rem' },
  title: { fontSize: '1.5rem', fontWeight: 700 },
  providerBadge: { display: 'flex', alignItems: 'center', gap: '.6rem', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: '.75rem 1rem' },
  card: { background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: '1.75rem' },
  sectionTitle: { fontWeight: 700, fontSize: '1.05rem', marginBottom: '.4rem' },
  stepsList: { display: 'flex', flexDirection: 'column', gap: '.6rem', marginBottom: '1.25rem' },
  step: { display: 'flex', alignItems: 'center', gap: '.85rem', background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 8, padding: '.85rem' },
  stepNum: { width: 24, height: 24, borderRadius: '50%', background: 'var(--primary)', color: '#0f1117', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '.75rem', fontWeight: 700, flexShrink: 0 },
  privacyNote: { display: 'flex', gap: '.75rem', alignItems: 'flex-start', background: 'rgba(0,200,150,.05)', border: '1px solid rgba(0,200,150,.2)', borderRadius: 8, padding: '.85rem' },
  errorBox: { marginTop: '1rem', padding: '.85rem', background: 'rgba(224,82,82,.1)', border: '1px solid rgba(224,82,82,.3)', borderRadius: 8, color: 'var(--danger)', fontSize: '.875rem' },
  infoBox: { background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: '1.25rem' },
}
