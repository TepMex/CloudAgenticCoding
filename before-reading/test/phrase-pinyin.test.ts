import { expect, test } from 'bun:test'
import { examplePinyin, splitPinyin } from '../src/phrase-pinyin'

test('splits compact, spaced, and apostrophe pinyin into syllables', () => {
  expect(splitPinyin('bǎoyòu')).toEqual(['bǎo', 'yòu'])
  expect(splitPinyin('lièzǔ lièzōng')).toEqual(['liè', 'zǔ', 'liè', 'zōng'])
  expect(splitPinyin("tái'àn")).toEqual(['tái', 'àn'])
  expect(splitPinyin('zhōng')).toEqual(['zhōng'])
  expect(splitPinyin('kāiqiào dàdiǎn')).toEqual(['kāi', 'qiào', 'dà', 'diǎn'])
  expect(splitPinyin('liǎngbìn wēishuāng')).toEqual(['liǎng', 'bìn', 'wēi', 'shuāng'])
  expect(splitPinyin('zōnghuángsè')).toEqual(['zōng', 'huáng', 'sè'])
  expect(splitPinyin('múyàng')).toEqual(['mú', 'yàng'])
  expect(splitPinyin('nǚ')).toEqual(['nǚ'])
  expect(splitPinyin('')).toEqual([])
  expect(splitPinyin('???')).toBeNull()
})

test('shows toned pinyin for the context phrase and keeps the sheet reading', () => {
  expect(examplePinyin('列祖列宗保佑', '保佑', 'bǎoyòu')).toBe('liè zǔ liè zōng bǎo yòu')
  expect(examplePinyin('大典中', '中', 'zhòng')).toBe('dà diǎn zhòng')
  expect(examplePinyin('中中', '中', 'zhòng')).toBe('zhōng zhōng')
  expect(examplePinyin('此次开窍大典', '开窍大典', 'kāiqiào dàdiǎn')).toBe('cǐ cì kāi qiào dà diǎn')
  expect(examplePinyin('在台案有三层', '台案', "tái'àn")).toBe('zài tái àn yǒu sān céng')
  expect(examplePinyin('先祖的牌位。牌位两侧', '牌位', 'páiwèi')).toBe('xiān zǔ de pái wèi。pái wèi liǎng cè')
  expect(examplePinyin('银行的一行人长大了', '', '')).toBe('yín háng de yī xíng rén zhǎng dà le')
  expect(examplePinyin('一个不走', '', '')).toBe('yī gè bù zǒu')
  expect(examplePinyin('', '中', 'zhōng')).toBe('')
  expect(examplePinyin('   ', '中', 'zhōng')).toBe('')
})
