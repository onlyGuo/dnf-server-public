export type ItemIcon = {
  path: string;
  index: number;
};

export type PvfItem = {
  id: number;
  rarity?: number;
  name?: string;
  type?: string; // equipment/stackable
  minimumLevel?: number;
  stackLimit?: number;
  attachType?: string;
  usableJobsStr?: string[];
  description?: string;
  explain?: string;
  // equipment / stackable extra fields (backend may return more)
  equipmentType?: string;
  equipmentTypeStr?: string;
  stackableType?: string;
  stackableTypeStr?: string;
  icon?: ItemIcon | null;
  iconMarks?: ItemIcon[];
  [key: string]: any;
};


export const rarityColor = (rarity?: number) => {
  switch (Number(rarity ?? 0)) {
    case 1:
      return '#15a9e8';
    case 2:
      return '#7111ff';
    case 3:
      return '#db00fd';
    case 4:
      return '#fdb500';
    case 5:
      return '#fd0000';
    case 6:
      return 'transparent';
    default:
      return '#e5e7eb';
  }
};

export const rarityGradient = (rarity?: number) => {
  return Number(rarity ?? 0) === 6
    ? 'linear-gradient(45deg, #db00fd, #f474fb, #7300ff, #5ef2f6)'
    : '';
};


export const getJobText = (it: PvfItem) => {
  if (Array.isArray(it.usableJobsStr) && it.usableJobsStr.length) return it.usableJobsStr.join(' / ');
  return '';
};
