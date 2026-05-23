import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'order', component: () => import('./views/OrderView.vue') },
    { path: '/orders', name: 'orders', component: () => import('./views/OrderListView.vue') },
    { path: '/queue', name: 'queue', component: () => import('./views/QueueView.vue') },
    { path: '/admin', name: 'admin', component: () => import('./views/AdminView.vue') }
  ]
})

export default router
