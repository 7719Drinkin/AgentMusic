export const CHAT_SUGGESTIONS = [
  '给我来点适合深夜学习的粤语歌',
  '生成一个通勤英文流行歌单',
  '播放上一版推荐歌单',
  '把当前播放切成随机模式',
]

export const AGENT_CHAT_HISTORY = [
  {
    id: 'a1',
    role: 'agent',
    message:
      '我可以根据自然语言生成推荐歌单、切换历史版本、查询歌曲信息，并控制当前播放状态。',
    metadata: 'Planner ready',
  },
  {
    id: 'u1',
    role: 'user',
    message: '给我来点适合夜间学习的粤语歌，别太伤感。',
  },
  {
    id: 'a2',
    role: 'agent',
    message:
      '已理解为“推荐并播放”。我会先生成新的推荐版本，再从候选里选择首播曲目并同步播放状态。',
    metadata: 'Intent: PLAY_RECOMMENDATION',
  },
]

export const RECOMMENDATION_PREVIEW = {
  title: 'Late Night Study Session',
  subtitle: 'Agent recommendation v08',
  summary:
    '围绕“夜间学习、粤语、低干扰”生成，优先保留最近高接受度艺人并降低情绪波动。',
  tags: ['粤语', '学习', '低干扰', '顺序播放'],
  tracks: [
    { title: 'K歌之王', artist: '陈奕迅', duration: '03:42' },
    { title: '小玩意', artist: '彭羚', duration: '04:12' },
    { title: '给自己的情书', artist: '王菲', duration: '04:23' },
    { title: '漩涡', artist: '黄耀明 / 彭羚', duration: '04:06' },
  ],
}

export const PLAYLIST_HISTORY = [
  {
    id: 'h1',
    name: 'Version 08 · Night Study',
    createdAt: 'Today 20:14',
    note: '当前正在使用的推荐版本',
  },
  {
    id: 'h2',
    name: 'Version 07 · Calm Cantopop',
    createdAt: 'Today 19:42',
    note: '减少重复艺人，适合长时工作',
  },
  {
    id: 'h3',
    name: 'Version 06 · Rainy Focus',
    createdAt: 'Yesterday 23:05',
    note: '偏安静，器乐占比更高',
  },
]

export const CURRENT_PLAYBACK_CARD = {
  trackTitle: 'K歌之王',
  artist: '陈奕迅',
  album: 'The Line-Up',
  device: 'Desktop Spotify',
  mode: 'SEQUENTIAL',
  progress: '01:12 / 03:42',
  lyricsPreview: '我唱得不够动人 你别皱眉...',
}

export const AGENT_FUNCTION_BLOCKS = [
  { title: '推荐歌单', description: '自然语言推荐、版本保存、历史切换' },
  { title: '播放控制', description: '播放、暂停、播放模式与当前会话同步' },
  { title: '音乐信息', description: '歌曲、艺人、歌词和元数据查询' },
  { title: '语音入口', description: '预留语音输入与语音控制按钮' },
]
