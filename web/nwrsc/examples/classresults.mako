<%inherit file="/base.mako" />

${resulttables()}

<%def name="resulttables()">
<table class='classresults'>
<%
if c.event.ispro:
	c.colcount = 6 + c.event.runs
	c.rowspan = "rowspan='%s'" % (c.event.courses)
elif c.event.courses > 1:
	c.colcount = 8 + c.event.runs
	c.rowspan = "rowspan='%s'" % (c.event.courses)
else:
	c.colcount = 7 + c.event.runs
	c.rowspan = ""


for code in sorted(c.results.keys()):
	entrants = c.results[code]
	c.curclass = c.classdata.classlist[code]
	classrow()
	if c.event.ispro:
		proheaderrow()
	else:
		headerrow()
	for e in entrants:
		if c.event.ispro:
			proentrantrow(e)
		else:
			entrantrow(e)
		for runs in e.runs[1:]:
			secondaryrow(runs)
%>
</table>
</%def>


<%def name="classrow()">
<tr>
	<th class='classhead' colspan='${c.colcount}'>
	<a name='${c.curclass.code}'>${c.curclass.code}</a> - ${c.curclass.descrip}
	</th>
</tr>
</%def>


<%def name="headerrow()">
<tr>
	<th class='pos'></th>
	<th class='trophy'></th>
	<th class='name'>Name</th>
	%if not c.ismobile:
	<th class='carnum'>#</th>
	<th class='caryear'>Year</th>
	<th class='cardesc'>Make/Model</th>
	%endif
	%for ii in range(1, int(c.event.runs)+1):
	<th class='run'>Run${ii}</th>
	%endfor
	%if c.event.courses > 1:
	<th class='total'>Total</th>
	%endif
	<th class='points'>Points</th>
</tr>
</%def>

<%def name="proheaderrow()">
<tr>
<th class='pos'>#</th>
<th class='trophy'>T</th>
<th class='name'>Entrant</th>
%for ii in range(1, int(c.event.runs)+1):
	<th class='run'>Run ${ii}</th>
%endfor
<th></th>  <%doc> Extra col to fix firefox border bug </%doc>
<th class='total'>Total</th>
<th class='points'>Points</th>
</tr>
</%def>


<%def name="entrantrow(entrant)">
<tr>
	<td class='pos' ${c.rowspan}>${entrant.position}</td>
	<td class='trophy' ${c.rowspan}>${entrant.trophy and 'T' or ''}</td>
	<td class='name' ${c.rowspan}>${entrant.firstname+" "+entrant.lastname}</td>
	%if not c.ismobile:
	<td class='carnum' ${c.rowspan}>${entrant.number}</td>
	<td class='caryear' ${c.rowspan}>${entrant.year}</td>
	<td class='cardesc' ${c.rowspan}>${entrant.make} ${entrant.model} ${entrant.color} \
						 ${entrant.indexstr and "(%s)"%entrant.indexstr or ""}</td>
	%endif
	${secondaryrow(entrant.runs[0])}
	%if c.event.courses > 1:
	<td class='total' ${c.rowspan}>${"%0.3f"%entrant.sum}</td>
	%endif
	<td class='points' ${c.rowspan}>${"%0.3f"%entrant.points}</td>
</tr>
</%def>


<%def name="proentrantrow(entrant)">
<tr>
	<td class='pos' ${c.rowspan}>${entrant.position}</td>
	<td class='trophy' ${c.rowspan}>${entrant.trophy and 'T' or ''}</td>
	<td class='name' align='center' ${c.rowspan}>#${entrant.number} - ${entrant.firstname} ${entrant.lastname}<br/>
		${entrant.make} ${entrant.model} ${entrant.color} ${entrant.indexstr and "(%s)"%entrant.indexstr or ""}</td>
	${secondaryrow(entrant.runs[0])}
	<td ${c.rowspan}></td> <%doc> Extra col to fix firefox border bug </%doc>
	<td class='total' ${c.rowspan}>${"%0.3f"%entrant.sum}</td>
	<td class='points' ${c.rowspan}>${entrant.ppoints}</td>
</tr>
</%def>


<%def name="secondaryrow(runs)">
<%
for run in runs:
	if run is not None:
		if c.event.ispro:
			proruncell(run)
		else:
			runcell(run)
	else:
		blankcell()
%>
</%def>


<%def name="runcell(run)">
	<td class='run ${run.norder==1 and 'bestnet' or run.rorder==1 and 'bestraw' or ''}'>
	%if run.status == "OK":
	<span class='net'>${"%0.3f (%d,%d)"%(run.net, run.cones, run.gates)}</span>
	%else:
	<span class='net'>${run.status}</span>
	%endif
	%if c.curclass.carindexed or c.curclass.classindexed:
	<span class='raw'>[${"%0.3f"%run.raw}]</span>
	%endif
	</td>
</%def>


<%def name="proruncell(run)">
<td class='run ${run.norder==1 and 'bestnet' or run.rorder==1 and 'bestraw' or ''}'>
%if run.status == "OK":
<span class='net'>${"%0.3f"%run.net}</span>
%else:
<span class='net'>${run.status}</span>
%endif

<span class='raw'>${"%0.3f (%d,%d)"%(run.raw,run.cones,run.gates)}</span>
<span class='reaction'>
%if run.reaction != 0:
	${"%0.3f"%run.reaction}
%endif
/ 
%if run.sixty > 1.0:
	${"%0.3f"%run.sixty}
%endif
</span>
</td>
</%def>


<%def name="blankcell()">
	<td class='run'>-</td>
</%def>

