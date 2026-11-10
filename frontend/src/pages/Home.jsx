import React from 'react';
import { Link } from 'react-router-dom';
import HomeHeader from '../components/HomeHeader';
import { useAuth } from '../auth';

export default function Home() {
  const { user } = useAuth();
  const motionStripRef = React.useRef(null);
  const [motionStripActive, setMotionStripActive] = React.useState(false);

  React.useEffect(() => {
    if (motionStripActive) return undefined;
    if (typeof window === 'undefined') return undefined;
    if (!('IntersectionObserver' in window)) {
      setMotionStripActive(true);
      return undefined;
    }

    const target = motionStripRef.current;
    if (!target) return undefined;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        setMotionStripActive(true);
        observer.disconnect();
      },
      {
        threshold: 0.45,
      },
    );

    observer.observe(target);
    return () => observer.disconnect();
  }, [motionStripActive]);

  return (
    <div className="home-page">
      <HomeHeader />
      <main className="home-content">
        <section className="home-hero" aria-labelledby="home-title">
          <div className="home-hero__content">
            <p className="home-eyebrow">Momentum Training System</p>
            <h1 id="home-title">Feel your progress <em>move.</em></h1>
            <p className="home-lead">
              Log. Review. Adapt. A focused flow that turns workouts into visible momentum.
            </p>

            <div className="home-hero__actions">
              <Link className="home-cta home-cta--primary" to={user ? '/dashboard' : '/signin'}>
                {user ? 'Open Dashboard' : 'Start Training'}
              </Link>
              <span className="home-hero__meta">No clutter. Just your lifts and cardio trends.</span>
            </div>
          </div>

          <div className="home-hero__media" aria-hidden="true">
            <img src="/training.png" alt="" className="home-hero__runner" />
          </div>
        </section>

        <section
          ref={motionStripRef}
          className={motionStripActive ? 'home-motion-strip is-active' : 'home-motion-strip'}
          aria-label="Training loop"
        >
          <div className="home-motion-strip__track">
            <span>Log</span>
            <span>Recover</span>
            <span>Progress</span>
            <span>Repeat</span>
          </div>
        </section>

        <section className="home-section home-story" aria-labelledby="story-title">
          <h2 id="story-title">A training story in three beats</h2>
          <div className="home-story-grid">
            <article className="home-story-card">
              <div className="home-story-card__index">01</div>
              <h3>Capture the session</h3>
              <p>Quick forms keep your focus on the workout, not admin work.</p>
            </article>
            <article className="home-story-card">
              <div className="home-story-card__index">02</div>
              <h3>See the pattern</h3>
              <p>Dashboard trends reveal what is improving and what needs attention.</p>
            </article>
            <article className="home-story-card">
              <div className="home-story-card__index">03</div>
              <h3>Train again, smarter</h3>
              <p>Use your history to adjust the next session with confidence.</p>
            </article>
          </div>
          
          <div className="home-story__media" aria-hidden="true">
            <img src="/runners.png" alt="" className="home-story__banner" />
          </div>
        </section>

        <section className="home-section home-section--compact" aria-labelledby="signal-title">
          <h2 id="signal-title">Built to reduce noise</h2>
          <div className="home-quality-grid">
            <div className="home-quality-item">Fast entry for lifting and cardio sessions</div>
            <div className="home-quality-item">Clean history you can skim in seconds</div>
            <div className="home-quality-item">Progress charts with clear trend signals</div>
            <div className="home-quality-item">Dark UI that keeps your numbers in focus</div>
          </div>
        </section>

        <section className="home-final-cta" aria-label="Call to action">
          <Link className="home-cta home-cta--primary" to={user ? '/dashboard' : '/signin?mode=create'}>
            {user ? 'Continue Training' : 'Create Your Account'}
          </Link>
        </section>
      </main>
    </div>
  );
}