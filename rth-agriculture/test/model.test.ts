import { describe, expect, test } from 'bun:test'
import { characters, gardenRegions, plots, sourceRthListIds } from '../src/data/model'

describe('V2 world model', () => {
  test('splits all 110 source lists into 220 ordered half-plots without losing characters', () => {
    expect(sourceRthListIds).toHaveLength(110)
    expect(plots).toHaveLength(220)
    expect(characters).toHaveLength(2974)
    expect(new Set(characters.map((character) => character.id)).size).toBe(2974)
    expect(new Set(plots.flatMap((plot) => plot.characterIds)).size).toBe(2974)

    sourceRthListIds.forEach((listId) => {
      const halves = plots.filter((plot) => plot.sourceRthListId === listId)
      expect(halves).toHaveLength(2)
      expect(halves.map((plot) => plot.sourceHalf)).toEqual([0, 1])
      expect(Math.abs(halves[0]!.characters.length - halves[1]!.characters.length)).toBeLessThanOrEqual(1)
      const frames = halves.flatMap((plot) => plot.characters.map((character) => character.frame))
      expect(frames).toEqual([...frames].sort((left, right) => left - right))
    })
  })

  test('occupies the 15 by 15 world geometry exactly once', () => {
    expect(gardenRegions).toHaveLength(15)
    const cells = plots.flatMap((plot) => plot.cells)
    expect(cells).toHaveLength(225)
    expect(new Set(cells.map((cell) => `${cell.x}:${cell.y}`)).size).toBe(225)
    expect(cells.every((cell) => cell.x >= 0 && cell.x < 15 && cell.y >= 0 && cell.y < 15)).toBe(true)
    expect(plots.slice(0, 5).every((plot) => plot.cells.length === 2)).toBe(true)
    expect(plots.slice(5).every((plot) => plot.cells.length === 1)).toBe(true)
    expect(plots.filter((plot) => plot.gardenId === gardenRegions[0]!.id)).toHaveLength(10)
    gardenRegions.slice(1).forEach((region) => {
      expect(plots.filter((plot) => plot.gardenId === region.id)).toHaveLength(15)
    })
  })

  test('has symmetrical adjacency derived from shared cell edges', () => {
    const byId = new Map(plots.map((plot) => [plot.id, plot]))
    plots.forEach((plot) => plot.neighbors.forEach((neighborId) => {
      expect(byId.get(neighborId)?.neighbors).toContain(plot.id)
    }))
  })
})
