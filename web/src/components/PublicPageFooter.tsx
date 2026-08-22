import { Link } from 'react-router-dom'

export function PublicPageFooter() {
  return (
    <footer className="public-page-footer" aria-label="KREVO public information">
      <nav className="public-page-footer-links" aria-label="Public information links">
        <Link to="/about">About</Link>
        <Link to="/help">Help</Link>
        <Link to="/privacy">Privacy</Link>
        <Link to="/terms">Terms</Link>
      </nav>
      <p>KREVO © 2026</p>
    </footer>
  )
}
