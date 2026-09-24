import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import './styles.css'

// The two future flags opt in to behaviour that becomes the default in React Router v7.
// Without them the router prints a warning for each one on every page load, which is noise
// in anybody's console and the sort of thing a student reasonably asks about. Opting in now
// is also less work than being surprised by it later.
ReactDOM.createRoot(document.getElementById('root')).render(
  <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <App />
  </BrowserRouter>,
)
