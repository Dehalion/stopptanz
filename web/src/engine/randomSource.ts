export type RandomSource = (min: number, max: number) => number

export const defaultRandomSource: RandomSource = (min, max) => {
  if (min === max) return min
  return min + Math.floor(Math.random() * (max - min + 1))
}
