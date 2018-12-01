package org.refractions.chyf.hygraph;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;

import net.refractions.chyf.ChyfDatastore;
import net.refractions.chyf.hygraph.DrainageArea;
import net.refractions.chyf.hygraph.ECatchment;
import net.refractions.chyf.pourpoint.Pourpoint;
import net.refractions.chyf.pourpoint.PourpointEngine;
import net.refractions.chyf.pourpoint.PourpointOutput;
import net.refractions.chyf.rest.GeotoolsGeometryReprojector;

public class SimpleDataPourpointTest {

	@Test
	public void test_simplePourpointTest() throws URISyntaxException, ParseException{
		
		URL url = ClassLoader.getSystemResource("data_small/" + ChyfDatastore.FLOWPATH_FILE);
		Path datapath = Paths.get(url.toURI()).getParent();
		ChyfDatastore datastore = new ChyfDatastore(datapath.toString() + "/");
		
		List<Pourpoint> points = new ArrayList<>();
		points.add(new Pourpoint(GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(new Coordinate(-73.4622349011061, 45.101463647914315)), ChyfDatastore.BASE_SRS), 0, "P1"));
		points.add(new Pourpoint(GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(new Coordinate(-73.46392456572985, 45.10495082809525)), ChyfDatastore.BASE_SRS), 2, "P2"));
		points.add(new Pourpoint(GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(new Coordinate(-73.46755554715536, 45.106892144897)), ChyfDatastore.BASE_SRS), 1, "P3"));
//		points.add(new Pourpoint(GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(new Coordinate(-73.47460180813951, 45.111997089079395)), ChyfDatastore.BASE_SRS), -1, "P4"));
		points.add(new Pourpoint(GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(new Coordinate(-73.474930904963, 45.1122588226033)), ChyfDatastore.BASE_SRS), -1, "P4"));
		
		points.add(new Pourpoint(GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(new Coordinate(-73.46838240431167, 45.11530451770461)), ChyfDatastore.BASE_SRS), 0, "P5"));
		
		HashMap<String, Coordinate> projectedPoints = new HashMap<>();
		projectedPoints.put("P1", new Coordinate(-73.46238158151098, 45.101257516041564));
		projectedPoints.put("P2", new Coordinate(-73.46455294608097, 45.104443757530134));
		projectedPoints.put("P3", new Coordinate(-73.46707833748302, 45.10751199007468));
		projectedPoints.put("P4", new Coordinate(-73.47452470185075, 45.112175703542384));
		projectedPoints.put("P5", new Coordinate(-73.4685416483889, 45.11506456249201));
		
		PourpointOutput results = ( new PourpointEngine(points, datastore.getHyGraph()) ).compute();
		
		//test point projection
		for(Pourpoint point : results.getPoints()) {
			Coordinate c = projectedPoints.get(point.getId());
			Point expected = GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(new Coordinate(c)), ChyfDatastore.BASE_SRS);
			Point actual = point.getProjectedPoint();
//			System.out.println(GeotoolsGeometryReprojector.reproject(actual, BasicTestSuite.TEST_DATA_SRID).toText());
			Assert.assertTrue("Pourpoint not projected to expected location ( " + c.x + " " + c.y +")", expected.equalsExact(actual, 0.00001));
		}
		
		
		//test pourpoint relationship
		Integer[][] ppRel = results.getPourpointRelationship();
		Integer[][] expectedRel = new Integer[][]{
				{null, -1, -1, -1, -1},
				{1, null, null, null, null},
				{1, null, null, null, null},
				{1, null, null, null, null},
				{1, null, null, null, null},
		};
		
		for (int i = 0; i < expectedRel.length; i ++) {
			for (int j = 0; j < expectedRel.length; j ++) {
				if (ppRel[i][j] != expectedRel[i][j]) {
					Assert.fail("Pourpoint relationship matrix incorrect");
				}
			}
			System.out.println();
		}
		
		//test catchments
		HashMap<String, String> expectedCatchments = new HashMap<>();
		expectedCatchments.put("P1", "POLYGON (( -73.4664080560344 45.10185657745563, -73.46654499005874 45.10274664861386, -73.46745788355436 45.10222173485388, -73.46864464509866 45.10217609017909, -73.46983140664298 45.10217609017909, -73.47106381286207 45.102929227312984, -73.47181694999595 45.10272382627647, -73.473163467902 45.10272382627647, -73.47457845282021 45.103317207048626, -73.47512618891759 45.10423010054424, -73.4753544122915 45.10525710572682, -73.47599343773844 45.105576618450286, -73.47624448344973 45.10639822259635, -73.48008498460628 45.10591972473696, -73.4848139793096 45.108540613367715, -73.48757730753987 45.11090511071937, -73.48717847666127 45.11489341950531, -73.48358899875393 45.115719569182396, -73.47703677717703 45.1154062020635, -73.47433042478657 45.115263762464004, -73.47484320734478 45.115719569182396, -73.47538447782286 45.11842592157285, -73.47413100934729 45.120106708846926, -73.47094036231853 45.12004973300712, -73.4708042649504 45.12261960389417, -73.4679554767237 45.12323684134329, -73.46501172888945 45.12261960389417, -73.46154570321363 45.123046922128175, -73.45855447557561 45.121527568407274, -73.45931415243606 45.11948593684481, -73.45803219773404 45.11734934567479, -73.46012130910029 45.114690476663206, -73.46045366772674 45.11369340078386, -73.45890031766238 45.11094841052457, -73.45781463537739 45.10877704595459, -73.45753141391174 45.10726653147112, -73.45696497098045 45.10405668819375, -73.46238158151098 45.101257516041564, -73.4652255970619 45.10061082702834, -73.4664080560344 45.10185657745563 ))");
		expectedCatchments.put("P2", "POLYGON (( -73.46455294608097 45.104443757530134, -73.46629394434744 45.10824683192499, -73.46564160921722 45.11175974831636, -73.46339741556099 45.11302868353096, -73.46045366772674 45.11369340078386, -73.45890031766238 45.11094841052457, -73.45781463537739 45.10877704595459, -73.45753141391174 45.10726653147112, -73.45918353912803 45.10622805276373, -73.46455294608097 45.104443757530134 ))");
		expectedCatchments.put("P3", "POLYGON (( -73.4676176399161 45.10548532910072, -73.46738941654219 45.105074527027696, -73.46684168044482 45.104321389893805, -73.46667088788585 45.10341487554239, -73.46654499005874 45.10274664861386, -73.46745788355436 45.10222173485388, -73.46864464509866 45.10217609017909, -73.46983140664298 45.10217609017909, -73.47106381286207 45.102929227312984, -73.47181694999595 45.10272382627647, -73.473163467902 45.10272382627647, -73.47457845282021 45.103317207048626, -73.47512618891759 45.10423010054424, -73.4753544122915 45.10525710572682, -73.47599343773844 45.105576618450286, -73.47624448344973 45.10639822259635, -73.47612516374025 45.107087729452836, -73.47578803670191 45.1074708724537, -73.47521355030347 45.10794236704982, -73.47438740062638 45.108426661688114, -73.47368838166199 45.108885857371924, -73.47288959985332 45.109182547757996, -73.4722049297316 45.10925101477017, -73.47102582607823 45.1091958355254, -73.47056172143948 45.10925101477017, -73.46982933344245 45.109053395925905, -73.46928367054561 45.108589166985844, -73.46912391418387 45.10790449686413, -73.46869028977345 45.10813272023803, -73.46831947368777 45.10814178248912, -73.46798279731435 45.10813272023803, -73.46782304095261 45.10801860855108, -73.46707833748302 45.10751199007468, -73.46718401550568 45.107105715055454, -73.4671383708309 45.106603623632864, -73.46727530485524 45.106238466234615, -73.4675948175787 45.10596459818593, -73.467978455945 45.105843612664195, -73.4676176399161 45.10548532910072 ))");
		expectedCatchments.put("P4", "POLYGON (( -73.47438740062638 45.108426661688114, -73.47521355030347 45.10794236704982, -73.47578803670191 45.1074708724537, -73.47612516374025 45.107087729452836, -73.47624448344973 45.10639822259635, -73.48008498460628 45.10591972473696, -73.4848139793096 45.108540613367715, -73.48757730753987 45.11090511071937, -73.48717847666127 45.11489341950531, -73.48358899875393 45.115719569182396, -73.47703677717703 45.1154062020635, -73.47433042478657 45.115263762464004, -73.47320822109108 45.11425377913806, -73.47233627039361 45.11346902351033, -73.47452470185075 45.112175703542384, -73.47376066638859 45.11013593688209, -73.47368838166199 45.108885857371924, -73.47438740062638 45.108426661688114 ))");
		expectedCatchments.put("P5", "POLYGON (( -73.45931415243606 45.11948593684481, -73.45803219773404 45.11734934567479, -73.46012130910029 45.114690476663206, -73.46045366772674 45.11369340078386, -73.46339741556099 45.11302868353096, -73.46486928947812 45.11397827960653, -73.46672100182546 45.11445307764431, -73.4685416483889 45.11506456249201, -73.47076943479914 45.115092834944605, -73.47320822109108 45.11425377913806, -73.47433042478657 45.115263762464004, -73.47484320734478 45.115719569182396, -73.47538447782286 45.11842592157285, -73.47413100934729 45.120106708846926, -73.47094036231853 45.12004973300712, -73.4708042649504 45.12261960389417, -73.4679554767237 45.12323684134329, -73.46501172888945 45.12261960389417, -73.46154570321363 45.123046922128175, -73.45855447557561 45.121527568407274, -73.45931415243606 45.11948593684481 ))");
		
		WKTReader reader = new WKTReader(BasicTestSuite.GF);
		
		for (Pourpoint point : results.getPoints()) {
			Geometry actual = results.getCatchment(point);
			Geometry expected = GeotoolsGeometryReprojector.reproject(reader.read(expectedCatchments.get(point.getId())), ChyfDatastore.BASE_SRS);
			Assert.assertTrue("Pourpoint catchment incorrect (" + point.getId() + ")", expected.equalsExact(actual, 0.00001));
		}
		
		
		//test non-overlapping combined coverages
		HashMap<String, String> expectedUniqueCoveratesCombined = new HashMap<>();
		expectedUniqueCoveratesCombined.put("P1", "POLYGON (( -73.46455294608097 45.104443757530134, -73.45918353912803 45.10622805276373, -73.45753141391174 45.10726653147112, -73.45696497098045 45.10405668819375, -73.46238158151098 45.101257516041564, -73.4652255970619 45.10061082702834, -73.4664080560344 45.10185657745563, -73.46654499005874 45.10274664861386, -73.46667088788585 45.10341487554239, -73.46684168044482 45.104321389893805, -73.46738941654219 45.105074527027696, -73.4676176399161 45.10548532910072, -73.467978455945 45.105843612664195, -73.4675948175787 45.10596459818593, -73.46727530485524 45.106238466234615, -73.4671383708309 45.106603623632864, -73.46718401550568 45.107105715055454, -73.46707833748302 45.10751199007468, -73.46782304095261 45.10801860855108, -73.46798279731435 45.10813272023803, -73.46831947368777 45.10814178248912, -73.46869028977345 45.10813272023803, -73.46912391418387 45.10790449686413, -73.46928367054561 45.108589166985844, -73.46982933344245 45.109053395925905, -73.47056172143948 45.10925101477017, -73.47102582607823 45.1091958355254, -73.4722049297316 45.10925101477017, -73.47288959985332 45.109182547757996, -73.47368838166199 45.108885857371924, -73.47376066638859 45.11013593688209, -73.47452470185075 45.112175703542384, -73.47233627039361 45.11346902351033, -73.47320822109108 45.11425377913806, -73.47076943479914 45.115092834944605, -73.4685416483889 45.11506456249201, -73.46672100182546 45.11445307764431, -73.46486928947812 45.11397827960653, -73.46339741556099 45.11302868353096, -73.46564160921722 45.11175974831636, -73.46629394434744 45.10824683192499, -73.46455294608097 45.104443757530134 ))");
		expectedUniqueCoveratesCombined.put("P2", "POLYGON (( -73.46455294608097 45.104443757530134, -73.46629394434744 45.10824683192499, -73.46564160921722 45.11175974831636, -73.46339741556099 45.11302868353096, -73.46045366772674 45.11369340078386, -73.45890031766238 45.11094841052457, -73.45781463537739 45.10877704595459, -73.45753141391174 45.10726653147112, -73.45918353912803 45.10622805276373, -73.46455294608097 45.104443757530134 ))");
		expectedUniqueCoveratesCombined.put("P3", "POLYGON (( -73.4676176399161 45.10548532910072, -73.46738941654219 45.105074527027696, -73.46684168044482 45.104321389893805, -73.46667088788585 45.10341487554239, -73.46654499005874 45.10274664861386, -73.46745788355436 45.10222173485388, -73.46864464509866 45.10217609017909, -73.46983140664298 45.10217609017909, -73.47106381286207 45.102929227312984, -73.47181694999595 45.10272382627647, -73.473163467902 45.10272382627647, -73.47457845282021 45.103317207048626, -73.47512618891759 45.10423010054424, -73.4753544122915 45.10525710572682, -73.47599343773844 45.105576618450286, -73.47624448344973 45.10639822259635, -73.47612516374025 45.107087729452836, -73.47578803670191 45.1074708724537, -73.47521355030347 45.10794236704982, -73.47438740062638 45.108426661688114, -73.47368838166199 45.108885857371924, -73.47288959985332 45.109182547757996, -73.4722049297316 45.10925101477017, -73.47102582607823 45.1091958355254, -73.47056172143948 45.10925101477017, -73.46982933344245 45.109053395925905, -73.46928367054561 45.108589166985844, -73.46912391418387 45.10790449686413, -73.46869028977345 45.10813272023803, -73.46831947368777 45.10814178248912, -73.46798279731435 45.10813272023803, -73.46782304095261 45.10801860855108, -73.46707833748302 45.10751199007468, -73.46718401550568 45.107105715055454, -73.4671383708309 45.106603623632864, -73.46727530485524 45.106238466234615, -73.4675948175787 45.10596459818593, -73.467978455945 45.105843612664195, -73.4676176399161 45.10548532910072 ))");
		expectedUniqueCoveratesCombined.put("P4", "POLYGON (( -73.47438740062638 45.108426661688114, -73.47521355030347 45.10794236704982, -73.47578803670191 45.1074708724537, -73.47612516374025 45.107087729452836, -73.47624448344973 45.10639822259635, -73.48008498460628 45.10591972473696, -73.4848139793096 45.108540613367715, -73.48757730753987 45.11090511071937, -73.48717847666127 45.11489341950531, -73.48358899875393 45.115719569182396, -73.47703677717703 45.1154062020635, -73.47433042478657 45.115263762464004, -73.47320822109108 45.11425377913806, -73.47233627039361 45.11346902351033, -73.47452470185075 45.112175703542384, -73.47376066638859 45.11013593688209, -73.47368838166199 45.108885857371924, -73.47438740062638 45.108426661688114 ))");
		expectedUniqueCoveratesCombined.put("P5", "POLYGON (( -73.45931415243606 45.11948593684481, -73.45803219773404 45.11734934567479, -73.46012130910029 45.114690476663206, -73.46045366772674 45.11369340078386, -73.46339741556099 45.11302868353096, -73.46486928947812 45.11397827960653, -73.46672100182546 45.11445307764431, -73.4685416483889 45.11506456249201, -73.47076943479914 45.115092834944605, -73.47320822109108 45.11425377913806, -73.47433042478657 45.115263762464004, -73.47484320734478 45.115719569182396, -73.47538447782286 45.11842592157285, -73.47413100934729 45.120106708846926, -73.47094036231853 45.12004973300712, -73.4708042649504 45.12261960389417, -73.4679554767237 45.12323684134329, -73.46501172888945 45.12261960389417, -73.46154570321363 45.123046922128175, -73.45855447557561 45.121527568407274, -73.45931415243606 45.11948593684481 ))");
				
		for (Pourpoint point : results.getPoints()) {
			Geometry actual = results.getCombinedUniqueCatchment(point);
			Geometry expected = GeotoolsGeometryReprojector.reproject(reader.read(expectedUniqueCoveratesCombined.get(point.getId())), ChyfDatastore.BASE_SRS);
			Assert.assertTrue("Pourpoint catchment incorrect (" + point.getId() + ")", expected.equalsExact(actual, 0.00001));
		}
		
		//test non-overlapping single coverages
		HashMap<String, String[]> expectedUniqueCoveratesSingle = new HashMap<>();
		expectedUniqueCoveratesSingle.put("P1", new String[]{
					"POLYGON (( -73.4685416483889 45.11506456249201, -73.47076943479914 45.115092834944605, -73.47320822109108 45.11425377913806, -73.47233627039361 45.11346902351033, -73.47144144673533 45.113060977189406, -73.46873046269931 45.111878321003445, -73.46564160921722 45.11175974831636, -73.46339741556099 45.11302868353096, -73.46486928947812 45.11397827960653, -73.46672100182546 45.11445307764431, -73.4685416483889 45.11506456249201 ))",
					"POLYGON (( -73.46873046269931 45.111878321003445, -73.46906015960516 45.110221400641784, -73.46982933344245 45.109053395925905, -73.46928367054561 45.108589166985844, -73.46912391418387 45.10790449686413, -73.46869028977345 45.10813272023803, -73.46831947368777 45.10814178248912, -73.46798279731435 45.10813272023803, -73.46782304095261 45.10801860855108, -73.46707833748302 45.10751199007468, -73.4668665897729 45.10814178248912, -73.4666386864137 45.10834119792842, -73.46629394434744 45.10824683192499, -73.46564160921722 45.11175974831636, -73.46873046269931 45.111878321003445 ))",
					"POLYGON (( -73.47452470185075 45.112175703542384, -73.47376066638859 45.11013593688209, -73.47368838166199 45.108885857371924, -73.47288959985332 45.109182547757996, -73.4722049297316 45.10925101477017, -73.47102582607823 45.1091958355254, -73.47056172143948 45.10925101477017, -73.46982933344245 45.109053395925905, -73.46906015960516 45.110221400641784, -73.46873046269931 45.111878321003445, -73.47144144673533 45.113060977189406, -73.47233627039361 45.11346902351033, -73.47452470185075 45.112175703542384 ))",
					"POLYGON (( -73.46707833748302 45.10751199007468, -73.4668665897729 45.10814178248912, -73.4666386864137 45.10834119792842, -73.46629394434744 45.10824683192499, -73.46455294608097 45.104443757530134, -73.4664080560344 45.10185657745563, -73.46654499005874 45.10274664861386, -73.46667088788585 45.10341487554239, -73.46684168044482 45.104321389893805, -73.46738941654219 45.105074527027696, -73.4676176399161 45.10548532910072, -73.467978455945 45.105843612664195, -73.4675948175787 45.10596459818593, -73.46727530485524 45.106238466234615, -73.4671383708309 45.106603623632864, -73.46718401550568 45.107105715055454, -73.46707833748302 45.10751199007468 ))",
					"POLYGON (( -73.46455294608097 45.104443757530134, -73.45918353912803 45.10622805276373, -73.45753141391174 45.10726653147112, -73.45696497098045 45.10405668819375, -73.46238158151098 45.101257516041564, -73.4652255970619 45.10061082702834, -73.4664080560344 45.10185657745563, -73.46455294608097 45.104443757530134 ))"					
				});
		expectedUniqueCoveratesSingle.put("P2", 
				new String[] {
					"POLYGON (( -73.46351446737359 45.10840885804923, -73.46045366772674 45.11369340078386, -73.46339741556099 45.11302868353096, -73.46564160921722 45.11175974831636, -73.46629394434744 45.10824683192499, -73.46351446737359 45.10840885804923 ))",
					"POLYGON (( -73.46351446737359 45.10840885804923, -73.46045366772674 45.11369340078386, -73.45890031766238 45.11094841052457, -73.45781463537739 45.10877704595459, -73.45753141391174 45.10726653147112, -73.45918353912803 45.10622805276373, -73.46192134662932 45.1066056813846, -73.46351446737359 45.10840885804923 ))",
					"POLYGON (( -73.46455294608097 45.104443757530134, -73.46629394434744 45.10824683192499, -73.46351446737359 45.10840885804923, -73.46192134662932 45.1066056813846, -73.45918353912803 45.10622805276373, -73.46455294608097 45.104443757530134 ))"
				});
		expectedUniqueCoveratesSingle.put("P3", new String[] {
				"POLYGON (( -73.46707833748302 45.10751199007468, -73.46782304095261 45.10801860855108, -73.46798279731435 45.10813272023803, -73.46831947368777 45.10814178248912, -73.46869028977345 45.10813272023803, -73.46912391418387 45.10790449686413, -73.46912391418387 45.10758498414066, -73.46910109184648 45.10728829375458, -73.46903262483431 45.107037248043284, -73.46851804660008 45.1065207149449, -73.46841642172477 45.10607870987288, -73.4683251323752 45.10591895351115, -73.467978455945 45.105843612664195, -73.4675948175787 45.10596459818593, -73.46727530485524 45.106238466234615, -73.4671383708309 45.106603623632864, -73.46718401550568 45.107105715055454, -73.46707833748302 45.10751199007468 ))",
				"POLYGON (( -73.46944342690735 45.10612435454766, -73.46934410920822 45.10567105054795, -73.46862182276128 45.105576618450286, -73.4676176399161 45.10548532910072, -73.467978455945 45.105843612664195, -73.4683251323752 45.10591895351115, -73.46841642172477 45.10607870987288, -73.46851804660008 45.1065207149449, -73.46944342690735 45.10612435454766 ))",
				"POLYGON (( -73.46934410920822 45.10567105054795, -73.46862182276128 45.105576618450286, -73.4676176399161 45.10548532910072, -73.46738941654219 45.105074527027696, -73.46684168044482 45.104321389893805, -73.46667088788585 45.10341487554239, -73.46654499005874 45.10274664861386, -73.46745788355436 45.10222173485388, -73.46864464509866 45.10217609017909, -73.46983140664298 45.10217609017909, -73.47106381286207 45.102929227312984, -73.46934410920822 45.10567105054795 ))",
				"POLYGON (( -73.46934410920822 45.10567105054795, -73.46944342690735 45.10612435454766, -73.47298088920287 45.10539403975117, -73.4753544122915 45.10525710572682, -73.47512618891759 45.10423010054424, -73.47457845282021 45.103317207048626, -73.473163467902 45.10272382627647, -73.47181694999595 45.10272382627647, -73.47106381286207 45.102929227312984, -73.46934410920822 45.10567105054795 ))",
				"POLYGON (( -73.46851804660008 45.1065207149449, -73.46903262483431 45.107037248043284, -73.46910109184648 45.10728829375458, -73.46912391418387 45.10758498414066, -73.46912391418387 45.10790449686413, -73.46928367054561 45.108589166985844, -73.46982933344245 45.109053395925905, -73.47056172143948 45.10925101477017, -73.47102582607823 45.1091958355254, -73.4722049297316 45.10925101477017, -73.47288959985332 45.109182547757996, -73.47368838166199 45.108885857371924, -73.47438740062638 45.108426661688114, -73.47521355030347 45.10794236704982, -73.47578803670191 45.1074708724537, -73.47612516374025 45.107087729452836, -73.47624448344973 45.10639822259635, -73.47599343773844 45.105576618450286, -73.4753544122915 45.10525710572682, -73.47298088920287 45.10539403975117, -73.46944342690735 45.10612435454766, -73.46851804660008 45.1065207149449 ))"
		});
		expectedUniqueCoveratesSingle.put("P4", new String[] {
				"POLYGON (( -73.47368838166199 45.108885857371924, -73.47438740062638 45.108426661688114, -73.47730971292964 45.112034092809566, -73.47703677717703 45.1154062020635, -73.47433042478657 45.115263762464004, -73.47320822109108 45.11425377913806, -73.47233627039361 45.11346902351033, -73.47452470185075 45.112175703542384, -73.47376066638859 45.11013593688209, -73.47368838166199 45.108885857371924 ))",
				"POLYGON (( -73.47730971292964 45.112034092809566, -73.4848139793096 45.108540613367715, -73.48008498460628 45.10591972473696, -73.47624448344973 45.10639822259635, -73.47612516374025 45.107087729452836, -73.47578803670191 45.1074708724537, -73.47521355030347 45.10794236704982, -73.47438740062638 45.108426661688114, -73.47730971292964 45.112034092809566 ))",
				"POLYGON (( -73.47730971292964 45.112034092809566, -73.47703677717703 45.1154062020635, -73.48358899875393 45.115719569182396, -73.48717847666127 45.11489341950531, -73.48757730753987 45.11090511071937, -73.4848139793096 45.108540613367715, -73.47730971292964 45.112034092809566 ))"
		});
		expectedUniqueCoveratesSingle.put("P5", new String[] {
				"POLYGON (( -73.46874679248627 45.11888172829124, -73.46846191328727 45.11683059805848, -73.4685416483889 45.11506456249201, -73.46672100182546 45.11445307764431, -73.46486928947812 45.11397827960653, -73.46339741556099 45.11302868353096, -73.46368229438366 45.11564007273877, -73.46391569778328 45.11678749307472, -73.46543904712345 45.11734934567479, -73.46672100182546 45.11815650233902, -73.46874679248627 45.11888172829124 ))",
				"POLYGON (( -73.46391569778328 45.11678749307472, -73.46259025889675 45.11782414371257, -73.46045366772674 45.119628376256145, -73.45931415243606 45.11948593684481, -73.45803219773404 45.11734934567479, -73.46012130910029 45.114690476663206, -73.46045366772674 45.11369340078386, -73.46339741556099 45.11302868353096, -73.46368229438366 45.11564007273877, -73.46391569778328 45.11678749307472 ))",
				"POLYGON (( -73.46423432193212 45.11826496505386, -73.46672100182546 45.11815650233902, -73.46543904712345 45.11734934567479, -73.46391569778328 45.11678749307472, -73.46259025889675 45.11782414371257, -73.46423432193212 45.11826496505386 ))",
				"POLYGON (( -73.46259025889675 45.11782414371257, -73.46045366772674 45.119628376256145, -73.45931415243606 45.11948593684481, -73.45855447557561 45.121527568407274, -73.46154570321363 45.123046922128175, -73.46501172888945 45.12261960389417, -73.46515416830078 45.120340573312816, -73.46423432193212 45.11826496505386, -73.46259025889675 45.11782414371257 ))",
				"POLYGON (( -73.46423432193212 45.11826496505386, -73.46515416830078 45.120340573312816, -73.46501172888945 45.12261960389417, -73.4679554767237 45.12323684134329, -73.4708042649504 45.12261960389417, -73.47094036231853 45.12004973300712, -73.46874679248627 45.11888172829124, -73.46672100182546 45.11815650233902, -73.46423432193212 45.11826496505386 ))",
				"POLYGON (( -73.4685416483889 45.11506456249201, -73.46846191328727 45.11683059805848, -73.46874679248627 45.11888172829124, -73.47094036231853 45.12004973300712, -73.47413100934729 45.120106708846926, -73.47538447782286 45.11842592157285, -73.47484320734478 45.115719569182396, -73.47433042478657 45.115263762464004, -73.47320822109108 45.11425377913806, -73.47076943479914 45.115092834944605, -73.4685416483889 45.11506456249201 ))"
		});
				
		for (Pourpoint point : results.getPoints()) {
			List<ECatchment> actualResults = new ArrayList<>(point.getUniqueCatchments());
			String[] parts = expectedUniqueCoveratesSingle.get(point.getId());
			
			Assert.assertEquals("Pourpoint unique catchment size incorrect: " + point.getId(), parts.length, actualResults.size());
			
			for (String p : parts) {
				Geometry expected = GeotoolsGeometryReprojector.reproject(reader.read(p), ChyfDatastore.BASE_SRS);
				ECatchment found = null;
				for (ECatchment a : actualResults) {
					if (a.getPolygon().equalsExact(expected, 0.00001)) {
						found = a;
						break;
					}
				}
				if (found != null) actualResults.remove(found);
				if (found == null) System.out.println(p);
				Assert.assertNotNull("Pourpoint catchment polygon incorrect (" + point.getId() + ")", found);
			}
		}
		
	}
}
