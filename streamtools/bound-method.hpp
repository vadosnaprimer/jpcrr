#ifndef _bound_method__hpp__included__
#define _bound_method__hpp__included__

#include <stdexcept>

template<typename ret, typename... args>
class bound_method_base;

template<typename ret, typename... args>
class bound_method
{
public:
	bound_method()
	{
		target = NULL;
	}
	bound_method(bound_method_base<ret, args...>& method)
	{
		target = &method;
		method.refcnt = 1;
	}
	~bound_method()
	{
		if(target) {
			target->refcnt--;
			if(!target->refcnt)
				delete target;
		}
	}
	bound_method& operator=(const bound_method& m)
	{
		if(target == m.target)
			return *this;
		if(target) {
			target->refcnt--;
			if(!target->refcnt)
				delete target;
		}
		target = m.target;
		if(target)
			target->refcnt++;
		return *this;
	}
	bool operator==(const bound_method& m)
	{
		return (target == m.target);
	}
	bool operator!=(const bound_method& m)
	{
		return (target != m.target);
	}
	void clear()
	{
		if(target) {
			target->refcnt--;
			if(!target->refcnt)
				delete target;
		}
		target = NULL;
	}
	operator bool()
	{
		return (target != NULL);
	}
	bool operator!()
	{
		return (target == NULL);
	}
	bound_method(const bound_method& m)
	{
		target = m.target;
		if(target)
			target->refcnt++;
	}
	ret operator()(args... arg)
	{
		if(!target)
			throw std::runtime_error("Attempted to call null pointer");
		return (*target)(arg...);
	}
private:
	bound_method_base<ret, args...>* target;
};

template<typename ret, typename... args>
class bound_method_base
{
public:
	virtual ~bound_method_base()
	{
	}
	virtual ret operator()(args... arg) = 0;
private:
	size_t refcnt;
	friend class bound_method<ret, args...>;
};

template<class T, typename ret, typename... args>
class bound_method_deriv_class : public bound_method_base<ret, args...>
{
public:
	bound_method_deriv_class(T& _object, ret (T::*_fun)(args... arg))
		: object(_object), fun(_fun)
	{
	}
	ret operator()(args... arg)
	{
		return (object.*fun)(arg...);
	}
private:
	T& object;
	ret (T::*fun)(args... arg);
};

template<typename ret, typename... args>
class bound_method_deriv_fun : public bound_method_base<ret, args...>
{
public:
	bound_method_deriv_fun(ret (*_fun)(args... arg))
		: fun(_fun)
	{
	}
	ret operator()(args... arg)
	{
		return fun(arg...);
	}
private:
	ret (*fun)(args... arg);
};

template<typename ret, typename tail, typename... args>
class bound_method_bind_tail : public bound_method_base<ret, args...>
{
public:
	bound_method_bind_tail(bound_method<ret, args..., tail> _fn, tail _t)
	{
		fn = _fn;
		t = _t;
	}

	ret operator()(args... arg)
	{
		return fn(arg..., t);
	}
private:
	bound_method<ret, args..., tail> fn;
	tail t;
};

template<class T, typename ret, typename... args>
bound_method<ret, args...> make_bound_method(T& _object, ret (T::*_fun)(args... arg))
{
	return bound_method<ret, args...>(*new bound_method_deriv_class<T, ret, args...>(_object, _fun));
}

template<typename ret, typename... args>
bound_method<ret, args...> make_bound_method(ret (*_fun)(args... arg))
{
	return bound_method<ret, args...>(*new bound_method_deriv_fun<ret, args...>(_fun));
}

template<typename ret, typename tail, typename... args>
bound_method<ret, args...> bind_last(bound_method<ret, args..., tail> fn, tail t)
{
	return bound_method<ret, args...>(*new bound_method_bind_tail<ret, tail, args...>(fn, t));
}

#endif

