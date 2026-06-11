# KYC Demo — Integración con Didit

Mini proyecto para demostrar un flujo KYC completo usando Didit como proveedor.
Stack: Spring Boot + PostgreSQL + React + Docker Compose.

## Paso 0 — Credenciales de Didit

En el dashboard de Didit (dashboard.didit.me) → Desarrolladores necesitás:

| Variable          | Dónde está en Didit                                      |
|-------------------|----------------------------------------------------------|
| DIDIT_CLIENT_ID   | Desarrolladores → Info de la app → "ID de la app"       |
| DIDIT_API_KEY     | Desarrolladores → Claves API → Primary (valor completo) |
| DIDIT_WORKFLOW_ID | Flujos de trabajo → Custom KYC → copiar ID completo      |

## Paso 1 — Configurar el .env

```bash
cp .env.example .env
# Editar .env con tus credenciales reales
```

## Paso 2 — Levantar el proyecto

```bash
docker compose up --build
# Frontend: http://localhost:3000
# Backend:  http://localhost:8080
```

## Paso 3 — Configurar el webhook en Didit (IMPORTANTE)

Didit necesita una URL pública para enviarte el resultado de la verificación.
En desarrollo local usás ngrok para exponer el puerto 8080:

```bash
# Instalar ngrok si no lo tenés
# https://ngrok.com/download

ngrok http 8080
# Te da una URL tipo: https://abc123.ngrok.io
```

En Didit Dashboard → Desarrolladores → Webhooks → Añadir destino:
  URL: https://abc123.ngrok.io/api/kyc/webhook
  Eventos: seleccionar "session.status_changed" o todos

Cada vez que reiniciás ngrok la URL cambia → actualizarla en Didit.

## Flujo completo

### Como usuario:
1. http://localhost:3000/register → registrarse
2. En el dashboard → "Iniciar verificación"
3. Se redirige al widget de Didit
4. Completar el proceso (DNI + liveness)
5. Didit llama al webhook → estado cambia automáticamente
6. Volver al dashboard → ver resultado

### Como admin:
1. Login con admin / admin123
2. Panel Admin → ver usuarios
3. Puede hacer override manual si el webhook no llegó

## Modo sandbox de Didit

En el dashboard de Didit podés activar el modo sandbox para hacer
verificaciones de prueba sin usar documentos reales.
Didit provee documentos ficticios para testear distintos escenarios
(aprobado, rechazado por documento falso, liveness fallido, etc.)

## Archivos modificados respecto al kyc-demo original

Backend (modificados):
- application.yml        → variables de Didit
- model/User.java        → diditSessionId en lugar de campos de archivos
- service/DiditService.java  → NUEVO: OAuth2 + creación de sesiones Didit
- service/KycService.java    → orquesta el flujo con DiditService
- service/UserService.java   → sin campos de archivos
- controller/KycController.java → endpoint /initiate + webhook Didit
- repository/UserRepository.java → findByDiditSessionId
- dto/UserDtos.java       → KycSessionResponse con verificationUrl

Frontend (modificados):
- src/pages/KycPage.jsx  → redirección al widget de Didit (sin formulario de fotos)
- src/services/api.js    → endpoint initiate
- package.json           → sin @sumsub/websdk-react

Sin cambios:
- LoginPage, RegisterPage, DashboardPage, AdminPage
- App.jsx, index.css, main.jsx, vite.config.js
- nginx.conf, Dockerfiles
