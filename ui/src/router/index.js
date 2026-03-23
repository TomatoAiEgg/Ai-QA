import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import LoginView from '../views/LoginView.vue'
import WorkspaceShell from '../views/WorkspaceShell.vue'
import { authState, ensureSession } from '../auth.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { guestOnly: true }
  },
  {
    path: '/',
    component: WorkspaceShell,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Chat',
        component: ChatView,
        meta: { requiresAuth: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  await ensureSession()

  if (to.meta.guestOnly && authState.isAuthenticated) {
    return '/'
  }

  if (to.meta.requiresAuth && !authState.isAuthenticated) {
    return {
      path: '/login',
      query: to.fullPath !== '/' ? { redirect: to.fullPath } : {}
    }
  }

  return true
})

export default router
