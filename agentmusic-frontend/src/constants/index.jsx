import * as Icon from '../component/icons'
import React from 'react'

export default {
  MOBILE_SIZE: 640,
}

export const MENU = [
  {
    title: 'Agent Chat',
    path: '/',
    icon: <Icon.Home />,
    iconSelected: <Icon.HomeActive />,
  },
  {
    title: 'Music',
    path: '/music',
    icon: <Icon.Library />,
    iconSelected: <Icon.LibraryActive />,
  },
]

export const LIBRARYTABS = [
  {
    title: 'Playlists',
    path: '/library',
  },
  {
    title: 'Podcasts',
    path: '/library/podcasts',
  },
  {
    title: 'Artists',
    path: '/library/artists',
  },
  {
    title: 'Albums',
    path: '/library/albums',
  },
]
