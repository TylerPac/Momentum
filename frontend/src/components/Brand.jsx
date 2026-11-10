import React from 'react';

/* Text wordmark with a small rising-bars mark, in place of the logo image (which has a grey box baked in). */
export default function Brand({ size = 'md' }) {
  // Unique per instance: a gradient defined inside a hidden copy would not paint the visible one
  const gradientId = `brand-gradient-${React.useId().replace(/:/g, '')}`;
  const fill = `url(#${gradientId})`;

  return (
    <span className={`brand brand--${size}`}>
      <svg className="brand__mark" viewBox="0 0 32 32" aria-hidden="true">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="1" x2="1" y2="0">
            <stop offset="0%" stopColor="#2f8cff" />
            <stop offset="100%" stopColor="#2ee6b8" />
          </linearGradient>
        </defs>
        <rect x="3" y="20" width="5" height="9" fill={fill} opacity="0.55" />
        <rect x="11" y="15" width="5" height="14" fill={fill} opacity="0.75" />
        <rect x="19" y="10" width="5" height="19" fill={fill} />
        <path d="M3 14 L13 8 L18 11 L28 3" fill="none" stroke={fill} strokeWidth="2.5" strokeLinecap="square" />
        <path d="M23 3 H28 V8" fill="none" stroke="#2ee6b8" strokeWidth="2.5" strokeLinecap="square" />
      </svg>
      <span className="brand__word">Momentum</span>
    </span>
  );
}
