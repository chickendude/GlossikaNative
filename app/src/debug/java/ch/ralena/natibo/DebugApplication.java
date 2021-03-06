package ch.ralena.natibo;

import com.facebook.stetho.Stetho;
import com.uphyca.stetho_realm.RealmInspectorModulesProvider;

public class DebugApplication extends MainApplication {
	@Override
	public void onCreate() {
		super.onCreate();
		Stetho.initialize(
				Stetho.newInitializerBuilder(this)
						.enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
						.enableWebKitInspector(RealmInspectorModulesProvider.builder(this)
								.withLimit(5000).build())
						.build());
	}
}
