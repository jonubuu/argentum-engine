import { useCallback, useState } from 'react'
import { useGameStore } from '@/store/gameStore.ts'
import type { OrderTriggersDecision } from '@/types'
import type { ResponsiveSizes } from '@/hooks/useResponsive.ts'

interface OrderTriggersUIProps {
  decision: OrderTriggersDecision
  responsive: ResponsiveSizes
}

/**
 * UI for CR 603.3b: the controller orders ≥ 2 of their own triggered abilities that triggered at
 * the same time. Unlike blocker/library ordering, the items being arranged are abilities (which may
 * share one source card — e.g. Bridge from Below's token-creation and exile abilities both live on
 * the same permanent), so this shows each ability's own rules text rather than a row of card faces.
 *
 * The list is shown in *resolution* order (top resolves first) with up/down controls; "Confirm
 * Order" submits the current arrangement as indices into the decision's original `triggers` list.
 */
export function OrderTriggersUI({ decision, responsive }: OrderTriggersUIProps) {
  const [order, setOrder] = useState<number[]>(decision.triggers.map((_, i) => i))
  const submitTriggerOrderDecision = useGameStore((s) => s.submitTriggerOrderDecision)

  const move = useCallback((position: number, direction: 'up' | 'down') => {
    const target = direction === 'up' ? position - 1 : position + 1
    if (target < 0 || target >= order.length) return
    setOrder((prev) => {
      const next = [...prev]
      const a = next[position]
      const b = next[target]
      if (a === undefined || b === undefined) return prev
      next[position] = b
      next[target] = a
      return next
    })
  }, [order.length])

  const handleConfirm = () => {
    submitTriggerOrderDecision(order)
  }

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.92)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: responsive.isMobile ? 16 : 24,
        padding: responsive.containerPadding,
        pointerEvents: 'auto',
        zIndex: 1000,
      }}
    >
      <h2
        style={{
          color: 'white',
          margin: 0,
          fontSize: responsive.isMobile ? 20 : 26,
          fontWeight: 600,
          textAlign: 'center',
        }}
      >
        Order Triggered Abilities
      </h2>

      <p
        style={{
          color: '#888',
          margin: 0,
          fontSize: responsive.fontSize.small,
          textAlign: 'center',
          maxWidth: 500,
        }}
      >
        These abilities triggered at the same time. Choose the order they resolve in — the top
        ability resolves first.
      </p>

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
          width: '100%',
          maxWidth: 560,
        }}
      >
        {order.map((triggerIndex, position) => {
          const option = decision.triggers[triggerIndex]
          if (!option) return null
          return (
            <div
              key={triggerIndex}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                backgroundColor: '#1a1a1a',
                border: '2px solid #333',
                borderRadius: 8,
                padding: responsive.isMobile ? '10px 12px' : '12px 16px',
              }}
            >
              <div
                style={{
                  color: position === 0 ? '#f87171' : '#666',
                  fontWeight: 700,
                  fontSize: responsive.fontSize.normal,
                  minWidth: 24,
                  textAlign: 'center',
                }}
              >
                {position + 1}
              </div>

              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{
                    color: 'white',
                    fontSize: responsive.fontSize.normal,
                    fontWeight: 600,
                  }}
                >
                  {option.sourceName}
                </div>
                <div
                  style={{
                    color: '#aaa',
                    fontSize: responsive.fontSize.small,
                    marginTop: 2,
                  }}
                >
                  {option.description}
                </div>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <button
                  onClick={() => move(position, 'up')}
                  disabled={position === 0}
                  style={{
                    width: 28,
                    height: 28,
                    backgroundColor: position === 0 ? '#1a1a1a' : '#333',
                    color: position === 0 ? '#444' : '#fff',
                    border: 'none',
                    borderRadius: 4,
                    cursor: position === 0 ? 'not-allowed' : 'pointer',
                    fontSize: 14,
                  }}
                  title="Resolve earlier"
                >
                  &#8593;
                </button>
                <button
                  onClick={() => move(position, 'down')}
                  disabled={position === order.length - 1}
                  style={{
                    width: 28,
                    height: 28,
                    backgroundColor: position === order.length - 1 ? '#1a1a1a' : '#333',
                    color: position === order.length - 1 ? '#444' : '#fff',
                    border: 'none',
                    borderRadius: 4,
                    cursor: position === order.length - 1 ? 'not-allowed' : 'pointer',
                    fontSize: 14,
                  }}
                  title="Resolve later"
                >
                  &#8595;
                </button>
              </div>
            </div>
          )
        })}
      </div>

      <button
        onClick={handleConfirm}
        style={{
          padding: responsive.isMobile ? '10px 24px' : '12px 36px',
          fontSize: responsive.fontSize.large,
          backgroundColor: '#dc2626',
          color: 'white',
          border: 'none',
          borderRadius: 8,
          cursor: 'pointer',
          fontWeight: 600,
        }}
      >
        Confirm Order
      </button>
    </div>
  )
}
