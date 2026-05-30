export const connectOrderEvents = (onChange: () => void): WebSocket => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const socket = new WebSocket(`${protocol}//${window.location.host}/ws/orders`)

  socket.onmessage = () => onChange()

  return socket
}
