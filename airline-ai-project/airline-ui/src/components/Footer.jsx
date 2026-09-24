/**
 * The footer.
 *
 * Real airline footers are enormous and mostly legal text. This one keeps the shape and the
 * four columns people scan for, without inventing pages that do not exist: every link goes
 * somewhere real in this application, and the rest is plain text rather than a dead anchor.
 * A footer full of links that go nowhere is the fastest way to make a site feel like a mockup.
 */
export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="shell footer-grid">
        <div>
          <h4>Book</h4>
          <a href="/">Find a flight</a>
          <a href="/plan">Plan a trip</a>
          <span>Group bookings</span>
          <span>Manage a booking</span>
        </div>

        <div>
          <h4>Travel information</h4>
          <span>Baggage allowance</span>
          <span>Check in and boarding</span>
          <span>Special assistance</span>
          <span>Travelling with children</span>
        </div>

        <div>
          <h4>Help</h4>
          <a href="/assistant">Ask the assistant</a>
          <a href="/support">Raise a ticket</a>
          <a href="/disruption">Disrupted flight</a>
          <span>Refunds and changes</span>
        </div>

        <div>
          <h4>About</h4>
          <span>Our fleet</span>
          <span>Careers</span>
          <span>Privacy</span>
          <span>Conditions of carriage</span>
        </div>
      </div>

      <div className="shell footer-base">
        <span>Telusko Airlines. A teaching project, built with LangChain4j and Spring Boot.</span>
        <span className="muted">Fares and schedules on this site are seeded demo data.</span>
      </div>
    </footer>
  )
}
