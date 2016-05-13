package com.arellomobile.mvp;

import com.arellomobile.mvp.presenter.PresenterType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Date: 17-Dec-15
 * Time: 16:05
 *
 * @author Alexander Blinov
 */
public class PresenterStore
{
	private Map<Class<? extends MvpPresenter>, Map<String, MvpPresenter>> mGlobalPresenters = new HashMap<>();
	private Map<Class<? extends MvpPresenter>, Map<String, MvpPresenter>> mWeakPresenters = new WeakValueHashMap<>();

	/**
	 *
	 * @param type     Type is presenter local, global or weak
	 * @param tag      Object to store presenter
	 * @param instance Instance of MvpPresenter implementation to store
	 * @param <T>      type of presenter
	 */
	public <T extends MvpPresenter> void add(PresenterType type, String tag, T instance)
	{
		Map<String, MvpPresenter> mvpPresenterMap;

		final Map<Class<? extends MvpPresenter>, Map<String, MvpPresenter>> presenters = getPresenters(type);

		if (presenters.containsKey(instance.getClass()))
		{
			mvpPresenterMap = presenters.get(instance.getClass());
		}
		else
		{
			mvpPresenterMap = createPresentersStore(type);
			presenters.put(instance.getClass(), mvpPresenterMap);
		}

		if (mvpPresenterMap.containsKey(tag))
		{
			throw new IllegalStateException("mvp multiple presenters map already contains tag");
		}

		mvpPresenterMap.put(tag, instance);
	}

	public MvpPresenter get(PresenterType type, String tag, Class<? extends MvpPresenter> clazz)
	{
		Map<String, MvpPresenter> tagMvpPresenterMap = getPresenters(type).get(clazz);

		if (tagMvpPresenterMap == null)
		{
			//TODO add builder
			return null;
		}

		//TODO add builder if tagMvpPresenterMap.getPresenterFactory(tag) == null

		return tagMvpPresenterMap.get(tag);
	}


	public MvpPresenter remove(PresenterType type, String tag, Class<? extends MvpPresenter> clazz)
	{
		Map<String, MvpPresenter> tagMvpPresenterMap = getPresenters(type).get(clazz);

		if (tagMvpPresenterMap == null)
		{
			return null;
		}

		return tagMvpPresenterMap.remove(tag);
	}

	public void removeAll(PresenterType type, String tag) {
		Map<Class<? extends MvpPresenter>, Map<String, MvpPresenter>> presenters = getPresenters(type);
		Iterator<Map<String, MvpPresenter>> iterator = presenters.values().iterator();
		while (iterator.hasNext()) {
			if (removeAll(iterator.next(), tag)) {
				iterator.remove();
			}
		}
	}

	private boolean removeAll(Map<String, MvpPresenter> presenters, String tag) {
		Iterator<Map.Entry<String, MvpPresenter>> iterator = presenters.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, MvpPresenter> entry = iterator.next();
			if (entry.getKey().startsWith(tag)) {
				entry.getValue().onDestroy();
				iterator.remove();
			}
		}
		return presenters.isEmpty();
	}

	private Map<Class<? extends MvpPresenter>, Map<String, MvpPresenter>> getPresenters(PresenterType type)
	{
		if (type == PresenterType.WEAK)
		{
			return mWeakPresenters;
		}

		return mGlobalPresenters;
	}

	private Map<String, MvpPresenter> createPresentersStore(PresenterType type)
	{
		if (type == PresenterType.WEAK)
		{
			return new WeakValueHashMap<>();
		}

		return new HashMap<>();
	}
}