import { useState } from 'react'
import reactLogo from './assets/react.svg'
import viteLogo from '/vite.svg'
import './App.css'

function App() {
  const [count, setCount] = useState(0)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)

  const fetchHello = async () => {
    setLoading(true)
    try {
      const response = await fetch('http://localhost:8080/hello')
      const data = await response.text()
      setMessage(data)
    } catch (error) {
      console.error('Error fetching hello:', error)
      setMessage('Error fetching from backend')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <div>
        <a href="https://vite.dev" target="_blank">
          <img src={viteLogo} className="logo" alt="Vite logo" />
        </a>
        <a href="https://react.dev" target="_blank">
          <img src={reactLogo} className="logo react" alt="React logo" />
        </a>
      </div>
      <h1>Vite + React</h1>
      <div className="card">
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>
        <p>
          Edit <code>src/App.tsx</code> and save to test HMR
        </p>
      </div>
      <div className="card">
        <button onClick={fetchHello} disabled={loading}>
          {loading ? 'Loading...' : 'Fetch Hello from Backend'}
        </button>
        {message && <p style={{ marginTop: '1rem' }}>Backend says: <strong>{message}</strong></p>}
      </div>
      <p className="read-the-docs">
        Click on the Vite and React logos to learn more
      </p>
    </>
  )
}

export default App
