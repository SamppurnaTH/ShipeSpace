import { useRef } from 'react'
import { LandingNav } from '../components/landing/LandingNav'
import { Hero } from '../components/landing/Hero'
import { TrustBar } from '../components/landing/TrustBar'
import { Problem } from '../components/landing/Problem'
import { Solution } from '../components/landing/Solution'
import { Features } from '../components/landing/Features'
import { UnderTheHood } from '../components/landing/UnderTheHood'
import { ProductPreview } from '../components/landing/ProductPreview'
import { FinalCTA } from '../components/landing/FinalCTA'
import { LandingFooter } from '../components/landing/LandingFooter'

export function LandingPage() {
  const featuresRef = useRef<HTMLElement>(null)
  const scrollToFeatures = () =>
    featuresRef.current?.scrollIntoView({ behavior: 'smooth' })

  return (
    <div style={{ background: '#ffffff', minHeight: '100vh', overflowX: 'hidden' }}>
      <LandingNav />
      <Hero onLearnMore={scrollToFeatures} />
      <TrustBar />
      <Problem />
      <Solution />
      <Features ref={featuresRef} />
      <UnderTheHood />
      <ProductPreview />
      <FinalCTA />
      <LandingFooter />
    </div>
  )
}
