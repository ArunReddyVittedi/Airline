import { Navigate, Route, Routes } from 'react-router-dom'
import { useState } from 'react'
import { currentUser } from './api.js'

import Header from './components/Header.jsx'
import Footer from './components/Footer.jsx'
import AssistantWidget from './components/AssistantWidget.jsx'

import Home from './pages/Home.jsx'
import Login from './pages/Login.jsx'
import MyTrips from './pages/MyTrips.jsx'
import Assistant from './pages/Assistant.jsx'
import Disruption from './pages/Disruption.jsx'
import TripPlanner from './pages/TripPlanner.jsx'
import Support from './pages/Support.jsx'
import Admin from './pages/Admin.jsx'

export default function App() {
  // Held in state as well as in localStorage so signing in or out re-renders the header.
  // Reading localStorage during render would work once and then never update.
  const [user, setUser] = useState(currentUser())

  return (
    <div className="app">
      <Header user={user} onSignedOut={() => setUser(null)} />

      <div className="app-body">
        <Routes>
          <Route path="/" element={<Home user={user} />} />
          <Route path="/login" element={<Login onSignedIn={setUser} />} />

          {/* Guarded. Redirecting rather than showing an empty page means a signed out
              visitor lands somewhere useful instead of wondering why the list is blank. */}
          <Route path="/trips" element={guard(user, <MyTrips />)} />
          <Route path="/assistant" element={guard(user, <Assistant />)} />
          <Route path="/disruption" element={guard(user, <Disruption />)} />
          <Route path="/support" element={guard(user, <Support />)} />

          {/* Signed in for a cost reason rather than a privacy one. One trip plan is three
              agents and several model calls, so an open endpoint is a free way for anyone
              to spend the airline's OpenAI budget. */}
          <Route path="/plan" element={guard(user, <TripPlanner />)} />

          <Route
            path="/admin"
            element={user?.role === 'ADMIN' ? <Admin /> : <Navigate to="/" replace />}
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>

      <Footer />

      {/* Outside the routes, so it survives navigation and the conversation is not thrown
          away every time somebody clicks a link. */}
      <AssistantWidget user={user} />
    </div>
  )
}

function guard(user, page) {
  return user ? page : <Navigate to="/login" replace />
}
