#include "rescalers/public.hpp"
#include "rescalers/factory.hpp"
#include "rescalers/simple.hpp"
#include <map>
#include <stdexcept>

namespace
{
	class simple_rescaler_c : public rescaler
	{
	public:
		simple_rescaler_c(bound_scaler_t _rescale_fn)
		{
			rescale_fn = _rescale_fn;
		}

		void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight)
		{
			rescale_fn(target, twidth, theight, source, swidth, sheight);
		}

	private:
		bound_scaler_t rescale_fn;
	};
}

simple_rescaler::simple_rescaler(const std::string& type, bound_scaler_t _fn)
	: rescaler_factory(type)
{
	fn = _fn;
}

rescaler& simple_rescaler::make(const std::string& type)
{
	return *new simple_rescaler_c(fn);
}
