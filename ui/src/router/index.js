import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import RagView from '../views/RagView.vue'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: ChatView
  },
  {
    path: '/rag',
    name: 'Rag',
    component: RagView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
