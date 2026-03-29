import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/app',
    name: 'App',
    component: () => import('@/views/AppView.vue')
  },
  {
    path: '/s/:token',
    name: 'Share',
    component: () => import('@/views/ShareView.vue')
  },
  {
    path: '/site/:token',
    name: 'Site',
    component: () => import('@/views/SiteView.vue')
  },
  {
    path: '/site/:token/:noteId',
    name: 'SiteNote',
    component: () => import('@/views/SiteView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

