import { describe, expect, it } from 'vitest';
import { MAX_WHEEL_SEGMENTS, segmentAngle, segmentLabel, targetRotation } from '../components/WheelDraw';

describe('WheelDraw geometry', () => {
  it('segmentAngle divides the circle evenly', () => {
    expect(segmentAngle(8)).toBe(45);
    expect(segmentAngle(4)).toBe(90);
    expect(segmentAngle(32)).toBeCloseTo(11.25);
  });

  it('targetRotation lands the winner segment under the pointer', () => {
    // After rotating clockwise by R, a point originally at angle c sits at
    // (c + R) mod 360. The pointer is at 0 (12 o'clock).
    const cases: Array<[number, number, number]> = [
      [0, 8, 0.5],
      [3, 8, 0.5],
      [7, 8, 0.21],
      [0, 1, 0.5],
      [17, 32, 0.79],
    ];
    for (const [winnerIndex, n, jitter] of cases) {
      const rotation = targetRotation(winnerIndex, n, 6, jitter);
      const landingPoint = winnerIndex * segmentAngle(n) + jitter * segmentAngle(n);
      const finalPosition = (landingPoint + rotation) % 360;
      expect(finalPosition % 360).toBeCloseTo(0, 5);
    }
  });

  it('targetRotation always spins forward at least `turns` full turns', () => {
    for (let index = 0; index < 16; index += 1) {
      const rotation = targetRotation(index, 16, 6, 0.5);
      expect(rotation).toBeGreaterThan(6 * 360 - segmentAngle(16));
      expect(rotation).toBeLessThanOrEqual(7 * 360);
    }
  });

  it('segmentLabel truncates long names and keeps short ones', () => {
    expect(segmentLabel('alice')).toBe('alice');
    expect(segmentLabel('a-very-long-participant-name')).toBe('a-very-long-par…');
    expect(segmentLabel('x'.repeat(16))).toBe('x'.repeat(16));
  });

  it('MAX_WHEEL_SEGMENTS is the documented cap', () => {
    expect(MAX_WHEEL_SEGMENTS).toBe(32);
  });
});
