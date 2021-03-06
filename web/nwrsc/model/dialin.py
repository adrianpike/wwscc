
from classlist import ClassData


class Entry(object):
	def __init__(self, row):
		self.firstname = row['firstname']
		self.lastname = row['lastname']
		self.classcode = row['classcode']
		self.indexcode = row['indexcode']
		self.carid = row['carid']
		self.raw = row['myraw']
		self.net = row['mynet']
		self.position = row['position']
		
		
class Dialins(list):

	def __init__(self, session, eventid):
		self.leaders = dict()
		self.classDial = dict()
		self.bonusDial = dict()

		classdata = ClassData(session)
		for r in session.sqlmap("GETDIALINS", (eventid, eventid)):
			entry = Entry(r)
			entry.indexVal = classdata.getEffectiveIndex(entry.classcode, entry.indexcode)
			entry.indexStr = classdata.getIndexStr(entry.classcode, entry.indexcode)

			if entry.position == 1:
				self.leaders[entry.classcode] = entry

			# Bonus dial is based on my best raw times
			entry.bonusDial = entry.raw/2.0
			self.bonusDial[entry.carid] = entry.bonusDial

			lead = self.leaders[entry.classcode]

			# Class dial is based on the class leaders best time, need to apply indexing though
			entry.classDial = lead.raw * lead.indexVal / entry.indexVal / 2.0
			self.classDial[entry.carid] = entry.classDial

			# Diff is the difference between my net and the class leaders net
			entry.diff = entry.net - lead.net

			# If we are #2, use the negative value to set the leaders diff
			if entry.position == 2:
				self.leaders[entry.classcode].diff = -entry.diff

			self.append(entry)
				

	def getDial(self, carid, bonus = False):
		if bonus:
			ret = bonusDial.get(carid, 0.0)
		else:
			ret = classDial.get(carid, 0.0)
		
		return (int(ret * 1000.0))/1000.0
