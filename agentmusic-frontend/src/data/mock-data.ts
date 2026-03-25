export type NavItem = {
  id: string
  label: string
  hint: string
  active?: boolean
}

export type ChatMessage = {
  id: string
  role: 'user' | 'agent'
  content: string
  meta?: string
}

export type PlaylistCard = {
  id: string
  name: string
  mood: string
  trackCount: number
  summary: string
}

export type QueueTrack = {
  id: string
  title: string
  artist: string
  length: string
}

export const navItems: NavItem[] = [
  { id: 'chat', label: 'Agent Chat', hint: '对话与控制', active: true },
  { id: 'history', label: 'History', hint: '推荐版本' },
  { id: 'library', label: 'Library', hint: '本地收藏' },
  { id: 'devices', label: 'Playback', hint: '本地会话' },
]

export const chatMessages: ChatMessage[] = [
  {
    id: 'm1',
    role: 'agent',
    content:
      '今晚可以走一条“放松但不太慢”的路线。我会优先参考你最近偏好的粤语和流行女声，再生成一个新歌单。',
    meta: 'Planner: PLAY_RECOMMENDATION',
  },
  {
    id: 'm2',
    role: 'user',
    content: '给我来点适合深夜写报告的歌，轻一点，先从粤语开始。',
  },
  {
    id: 'm3',
    role: 'agent',
    content:
      '已生成 18 首候选，并准备从节奏更稳的几首开始播放。你也可以继续补充“不要太伤感”这类限制。',
    meta: '候选池: 42 首，首播模式: 顺序播放',
  },
]

export const playlistCards: PlaylistCard[] = [
  {
    id: 'p1',
    name: 'Late Night Report Session',
    mood: '粤语 / 轻松 / 专注',
    trackCount: 18,
    summary: '围绕深夜工作场景生成，首段控制在低能量区间。',
  },
  {
    id: 'p2',
    name: 'Version 07 · Calm Cantopop',
    mood: '历史版本',
    trackCount: 20,
    summary: '保留上一轮高接受度曲目，并减少重复艺人密度。',
  },
  {
    id: 'p3',
    name: 'Morning Reset Mix',
    mood: '英语 / 清醒 / 通勤',
    trackCount: 16,
    summary: '适合短时通勤和切换状态，不走强刺激路线。',
  },
]

export const queueTracks: QueueTrack[] = [
  { id: 'q1', title: 'K歌之王', artist: '陈奕迅', length: '03:42' },
  { id: 'q2', title: '小玩意', artist: '彭羚', length: '04:12' },
  { id: 'q3', title: '给自己的情书', artist: '王菲', length: '04:23' },
]
