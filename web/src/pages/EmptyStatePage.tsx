
export function EmptyStatePage() {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '60vh',
      textAlign: 'center',
      padding: 'var(--space-6)',
    }}>
      <h1>No Content Yet</h1>
      <p>Be the first to share a ship!</p>
      <button onClick={() => window.location.href = '/feed'}>Browse Feed</button>
    </div>
  )
}